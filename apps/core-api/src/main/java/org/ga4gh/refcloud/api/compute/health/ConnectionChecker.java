package org.ga4gh.refcloud.api.compute.health;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputeHealthConfig;
import org.springframework.stereotype.Component;

// the checks behind a connection's health (D10). a connection is healthy only if every check passes; each failed
// check adds a reason (logged by ComputeHealthService, never exposed in service-info)
@Component
public class ConnectionChecker {

    private final ComputeHealthConfig healthConfig;

    private final HttpClient httpClient;

    public ConnectionChecker(ComputeHealthConfig healthConfig) {
        this.healthConfig = healthConfig;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(healthConfig.executorTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    // returns the reasons the connection is unhealthy; empty means healthy
    public List<String> check(ComputeConfig.Connection connection, Path storageRoot) {
        List<String> failures = new ArrayList<>();
        checkExecutor(connection.executor()).ifPresent(failures::add);
        checkEngine(connection.engine()).ifPresent(failures::add);
        checkStorage(storageRoot).ifPresent(failures::add);
        return failures;
    }

    // the executor answers its TES service-info
    Optional<String> checkExecutor(ComputeConfig.Executor executor) {
        URI serviceInfo = URI.create(stripTrailingSlash(executor.tesUrl().toString()) + "/ga4gh/tes/v1/service-info");
        HttpRequest.Builder request = HttpRequest.newBuilder(serviceInfo)
                .timeout(healthConfig.executorTimeout())
                .GET();
        if (executor.credentialEnv() != null) {
            String credential = System.getenv(executor.credentialEnv());
            if (credential == null || credential.isBlank()) {
                return Optional.of(
                        "executor credential environment variable " + executor.credentialEnv() + " is not set");
            }
            request.header("Authorization", "Bearer " + credential);
        }
        try {
            int status = httpClient
                    .send(request.build(), HttpResponse.BodyHandlers.discarding())
                    .statusCode();
            return status >= 200 && status < 300
                    ? Optional.empty()
                    : Optional.of("executor " + serviceInfo + " returned HTTP " + status);
        } catch (IOException exception) {
            return Optional.of("executor " + serviceInfo + " is unreachable: " + describe(exception));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.of("executor check was interrupted");
        }
    }

    // the launcher runs the pinned nextflow version: NXF_VER makes it fetch and run exactly that version (runs use
    // the same NXF_VER, Task 7), so what's installed on the host can't drift from what's configured
    @SuppressFBWarnings(
            value = "COMMAND_INJECTION",
            justification = "the executable and version come from the deployer's connections file, never from a"
                    + " request; the arguments are fixed and no shell is involved")
    Optional<String> checkEngine(ComputeConfig.Engine engine) {
        Path output = null;
        try {
            output = Files.createTempFile("refcloud-nextflow-version-", ".log");
            ProcessBuilder builder = new ProcessBuilder(engine.nextflowExecutable(), "-version")
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile());
            builder.environment().put("NXF_VER", engine.nextflowVersion());
            Process process = builder.start();
            if (!process.waitFor(healthConfig.engineTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return Optional.of("nextflow -version did not finish within " + healthConfig.engineTimeout());
            }
            String printed = Files.readString(output, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return Optional.of("nextflow -version exited with " + process.exitValue() + ": " + lastLine(printed));
            }
            // e.g. "version 26.10.5 build 14654"
            if (!printed.contains("version " + engine.nextflowVersion() + " ")) {
                return Optional.of(
                        "nextflow did not report version " + engine.nextflowVersion() + ": " + lastLine(printed));
            }
            return Optional.empty();
        } catch (IOException exception) {
            return Optional.of(
                    "nextflow (" + engine.nextflowExecutable() + ") could not be run: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.of("engine check was interrupted");
        } finally {
            deleteQuietly(output);
        }
    }

    // a file can be created in the storage root. a missing root is unhealthy, not created: setup creates it
    Optional<String> checkStorage(Path storageRoot) {
        if (!Files.isDirectory(storageRoot)) {
            return Optional.of("storage root " + storageRoot + " does not exist");
        }
        Path probe = null;
        try {
            probe = Files.createTempFile(storageRoot, ".refcloud-health-", ".tmp");
            return Optional.empty();
        } catch (IOException exception) {
            return Optional.of("storage root " + storageRoot + " is not writable: " + exception.getMessage());
        } finally {
            deleteQuietly(probe);
        }
    }

    // some exceptions (e.g. a refused connection) have no message
    private static String describe(Exception exception) {
        return exception.getMessage() != null
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String lastLine(String text) {
        String trimmed = text.strip();
        int newline = trimmed.lastIndexOf('\n');
        return newline < 0 ? trimmed : trimmed.substring(newline + 1);
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // a leftover temp file is harmless; the next check uses a new one
        }
    }
}
