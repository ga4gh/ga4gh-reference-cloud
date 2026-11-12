package org.ga4gh.refcloud.api.compute.wes;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.ga4gh.refcloud.api.compute.ComputeAsyncConfig;
import org.ga4gh.refcloud.api.compute.ComputeWesConfig;
import org.ga4gh.refcloud.api.compute.run.ComputeRun;
import org.ga4gh.refcloud.api.compute.run.ComputeRunService;
import org.ga4gh.refcloud.api.compute.run.ComputeRunStates;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

// prepares a WES run in the background (Task 8): puts its workflow into the run directory (the pinned commit for git
// and TRS entries, a copy for bundled ones) and downloads its DRS inputs, then hands it to the run service, which
// starts it when pooled capacity allows. a failure ends the run SYSTEM_ERROR with the reason. preparation runs on its
// own pool, never on the scheduler; a sweep picks up queued runs that aren't prepared yet (e.g. after a restart)
@Slf4j
@Service
public class RunPreparationService {

    private static final Pattern GITHUB_REPOSITORY =
            Pattern.compile("^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");

    private static final Pattern FULL_SHA = Pattern.compile("^[0-9a-f]{40}$");

    private final ComputeRunService computeRunService;

    private final ComputeWesConfig wesConfig;

    private final TaskExecutor executor;

    private final HttpClient httpClient;

    // runs being prepared right now, so the sweep never starts a second preparation of the same run
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    public RunPreparationService(
            ComputeRunService computeRunService,
            ComputeWesConfig wesConfig,
            @Qualifier(ComputeAsyncConfig.PREPARATION_EXECUTOR) TaskExecutor executor) {
        this.computeRunService = computeRunService;
        this.wesConfig = wesConfig;
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // starts preparing a run on the preparation pool and returns at once
    public void prepareInBackground(String runId) {
        if (inProgress.add(runId)) {
            executor.execute(() -> {
                try {
                    prepare(runId);
                } finally {
                    inProgress.remove(runId);
                }
            });
        }
    }

    @Scheduled(
            initialDelayString = "${ga4gh.refcloud.compute.wes.preparation-sweep-interval}",
            fixedDelayString = "${ga4gh.refcloud.compute.wes.preparation-sweep-interval}")
    public void sweep() {
        for (ComputeRun run : computeRunService.findUnprepared()) {
            prepareInBackground(run.getId());
        }
    }

    void prepare(String runId) {
        ComputeRun run = computeRunService.findRun(runId).orElse(null);
        if (run == null || !ComputeRunStates.QUEUED.equals(run.getState()) || run.getPreparedAt() != null) {
            return;
        }
        try {
            Path runDirectory = Path.of(run.getRunDirectory());
            putWorkflowInPlace(run.getWorkflowSource(), runDirectory.resolve("workflow"));
            Map<String, String> localPaths = stageInputs(run.getDrsInputs(), runDirectory.resolve("inputs"));
            JsonNode params = DrsInputs.rewrite(run.getWorkflowParams(), localPaths);
            if (computeRunService.markPrepared(runId, params)) {
                log.info("run {} prepared ({} DRS inputs)", runId, localPaths.size());
            }
        } catch (InterruptedException exception) {
            // core-api is shutting down: the run stays unprepared, and the sweep prepares it again after the restart
            Thread.currentThread().interrupt();
            log.info("preparation of run {} was interrupted; it will be retried", runId);
        } catch (IOException | RuntimeException exception) {
            if (Thread.currentThread().isInterrupted()) {
                // an interrupted download or copy surfaces as an IOException; same as above
                log.info("preparation of run {} was interrupted; it will be retried", runId);
                return;
            }
            if (!isStillWaiting(runId)) {
                // cancelled while being prepared: its directory was cleaned up underneath the download or copy
                log.info("run {} ended while it was being prepared", runId);
                return;
            }
            log.warn("could not prepare run {}: {}", runId, exception.getMessage());
            computeRunService.failPreparation(runId, "the run could not be prepared: " + exception.getMessage());
        }
    }

    private boolean isStillWaiting(String runId) {
        return computeRunService
                .findRun(runId)
                .map(run -> ComputeRunStates.QUEUED.equals(run.getState()))
                .orElse(false);
    }

    // the workflow, at exactly what the allowlist entry pinned
    private void putWorkflowInPlace(JsonNode source, Path workflowDirectory) throws IOException, InterruptedException {
        deleteRecursively(workflowDirectory); // a retry after a restart starts clean
        String kind = source.path("kind").asString("");
        switch (kind) {
            case "git", "trs" ->
                fetchCommit(
                        source.path("repository").asString(""),
                        source.path("commit").asString(""),
                        workflowDirectory);
            case "bundled" -> copyBundled(source.path("bundled_path").asString(""), workflowDirectory);
            default -> throw new IOException("unknown workflow source: " + kind);
        }
    }

    // git init + fetch exactly the pinned commit (GitHub serves a commit by its sha) + checkout, then check HEAD
    private void fetchCommit(String repository, String commit, Path directory)
            throws IOException, InterruptedException {
        if (!GITHUB_REPOSITORY.matcher(repository).matches()
                || !FULL_SHA.matcher(commit).matches()) {
            throw new IOException("the allowlisted git source is invalid: " + repository + " @ " + commit);
        }
        Files.createDirectories(directory);
        git(directory, "init", "-q");
        git(directory, "fetch", "-q", "--depth", "1", repository, commit);
        git(directory, "-c", "advice.detachedHead=false", "checkout", "-q", "--detach", "FETCH_HEAD");
        String head = git(directory, "rev-parse", "HEAD").strip();
        if (!head.equals(commit)) {
            throw new IOException("fetched " + head + " but the allowlist pins " + commit);
        }
    }

    @SuppressFBWarnings(
            value = "COMMAND_INJECTION",
            justification = "git runs with a fixed argument list (no shell); the repository and commit come from the"
                    + " admin's allowlist, and fetchCommit validates them as a github https url and a full sha")
    private String git(Path directory, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(arguments));
        Path output = Files.createTempFile("refcloud-git-", ".log");
        try {
            ProcessBuilder builder =
                    new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            Process process = builder.start();
            boolean exited;
            try {
                exited = process.waitFor(wesConfig.gitTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                // shutting down: don't leave git running
                process.destroyForcibly();
                throw exception;
            }
            if (!exited) {
                process.destroyForcibly();
                throw new IOException("git " + arguments[0] + " timed out after " + wesConfig.gitTimeout());
            }
            String printed = Files.readString(output, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException("git " + String.join(" ", arguments) + " failed: " + printed.strip());
            }
            return printed;
        } finally {
            Files.deleteIfExists(output);
        }
    }

    // a bundled workflow is copied from the bundled workflows base directory; the path must stay inside it
    private void copyBundled(String bundledPath, Path workflowDirectory) throws IOException {
        Path base =
                Path.of(wesConfig.bundledWorkflowsBaseDir()).toAbsolutePath().normalize();
        Path source = base.resolve(bundledPath).normalize();
        if (bundledPath.isBlank() || !source.startsWith(base) || !Files.isDirectory(source)) {
            throw new IOException("bundled workflow not found: " + bundledPath);
        }
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                Files.createDirectories(
                        workflowDirectory.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                // regular files only: a symlink could point outside the bundled workflow
                if (attributes.isRegularFile()) {
                    Files.copy(
                            file,
                            workflowDirectory.resolve(source.relativize(file).toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // downloads each DRS input into inputs/<object id>/<name>; returns drs uri -> local path
    private Map<String, String> stageInputs(JsonNode drsInputs, Path inputsDirectory)
            throws IOException, InterruptedException {
        Map<String, String> localPaths = new LinkedHashMap<>();
        if (drsInputs == null) {
            return localPaths;
        }
        for (JsonNode input : drsInputs) {
            String uri = input.path("uri").asString("");
            String objectId = input.path("object_id").asString("");
            String url = input.path("url").asString("");
            Path directory = inputsDirectory.resolve(DrsInputs.safeFileName(objectId, "object"));
            Path target =
                    directory.resolve(DrsInputs.safeFileName(input.path("name").asString(""), "input"));
            Files.createDirectories(directory);
            download(
                    url,
                    target,
                    input.path("size").isNumber() ? input.get("size").longValue() : null);
            localPaths.put(uri, target.toString());
        }
        return localPaths;
    }

    private void download(String url, Path target, Long expectedSize) throws IOException, InterruptedException {
        if (!url.startsWith("https://")) {
            throw new IOException("refusing to download a non-https input url");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(wesConfig.downloadTimeout())
                .GET()
                .build();
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException(
                        "downloading " + target.getFileName() + " failed (HTTP " + response.statusCode() + ")");
            }
            Files.copy(body, partial, StandardCopyOption.REPLACE_EXISTING);
        }
        long size = Files.size(partial);
        if (expectedSize != null && size != expectedSize) {
            Files.deleteIfExists(partial);
            throw new IOException(
                    "downloaded " + size + " bytes of " + target.getFileName() + ", expected " + expectedSize);
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
