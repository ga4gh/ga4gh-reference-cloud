package org.ga4gh.refcloud.api.compute.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

// runs in a directory on a filesystem every component mounts at the same path (D13): a host directory in native mode,
// a hostPath (or a shared volume) in Kubernetes. each run is runs/<run_id>/ under the connection's storage root
@Slf4j
public class SharedFilesystemRunStorage implements RunStorage {

    // removed when a run ends: the workflow's copy (the run records where it came from: repository and commit, or
    // bundled path), staged inputs, task work directories, and Nextflow's cache (in its launch directory, the run's)
    static final List<String> INTERMEDIATE_DIRECTORIES = List.of("workflow", "inputs", "work", ".nextflow");

    private final Path root;

    public SharedFilesystemRunStorage(Path root) {
        this.root = root;
    }

    @Override
    public ComputeConfig.StorageType type() {
        return ComputeConfig.StorageType.SHARED_FILESYSTEM;
    }

    @Override
    public String allocate(String runId) throws IOException {
        Path runDirectory = root.resolve("runs").resolve(runId);
        for (String child : List.of("inputs", "work", "outputs")) {
            Files.createDirectories(runDirectory.resolve(child));
        }
        return runDirectory.toString();
    }

    // the run's own workflow/, emptied first, so a retry after a restart starts clean
    @Override
    public Path workflowStaging(String location) throws IOException {
        Path workflow = directory(location).resolve("workflow");
        deleteRecursively(workflow);
        return Files.createDirectories(workflow);
    }

    @Override
    public void publishWorkflow(String location, Path staging) {
        // already in place
    }

    @Override
    public void discardWorkflowStaging(String location, Path staging) {
        // part of the run directory: removed with the rest when the run ends
    }

    // written to a .part file first and renamed, so a half-written input is never mistaken for a staged one
    @Override
    public String stageInput(String location, String relativePath, InputStream content, Long expectedSize)
            throws IOException {
        if (!RelativePaths.isSafe(relativePath)) {
            throw new IOException("invalid input path: " + relativePath);
        }
        int slash = relativePath.lastIndexOf('/');
        Path inputs = directory(location).resolve("inputs");
        Path directory = slash < 0 ? inputs : inputs.resolve(relativePath.substring(0, slash));
        String name = relativePath.substring(slash + 1);
        Path target = directory.resolve(name);
        Path partial = directory.resolve(name + ".part");
        Files.createDirectories(directory);
        Files.copy(content, partial, StandardCopyOption.REPLACE_EXISTING);
        long size = Files.size(partial);
        if (expectedSize != null && size != expectedSize) {
            Files.deleteIfExists(partial);
            throw new IOException("downloaded " + size + " bytes of " + name + ", expected " + expectedSize);
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        return target.toString();
    }

    // walk doesn't follow symlinks, and only regular files are listed
    @Override
    public List<StoredFile> listOutputs(String location) throws IOException {
        Path outputs = directory(location).resolve("outputs");
        if (!Files.isDirectory(outputs, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(outputs)) {
            return files.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    .map(file -> new StoredFile(outputs.relativize(file).toString(), size(file)))
                    .sorted(Comparator.comparing(StoredFile::relativePath))
                    .toList();
        }
    }

    @Override
    public Optional<Resource> openOutput(String location, String relativePath) {
        return resolveInside(directory(location).resolve("outputs"), relativePath)
                .map(FileSystemResource::new);
    }

    @Override
    public Optional<Resource> openEngineLog(String location, String name) {
        Path file = directory(location).resolve(name);
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                ? Optional.of(new FileSystemResource(file))
                : Optional.empty();
    }

    @Override
    public void writeEngineFile(String location, String name, String content) throws IOException {
        Files.writeString(engineFile(location, name), content, StandardCharsets.UTF_8);
    }

    @Override
    public Optional<String> readEngineFile(String location, String name) throws IOException {
        Path file = engineFile(location, name);
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                ? Optional.of(Files.readString(file, StandardCharsets.UTF_8))
                : Optional.empty();
    }

    @Override
    public String readEngineFileTail(String location, String name, long bytes) throws IOException {
        Path file = engineFile(location, name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return "";
        }
        try (RandomAccessFile reader = new RandomAccessFile(file.toFile(), "r")) {
            long start = Math.max(0, reader.length() - bytes);
            byte[] buffer = new byte[(int) (reader.length() - start)];
            reader.seek(start);
            reader.readFully(buffer);
            return new String(buffer, StandardCharsets.UTF_8);
        }
    }

    private static Path engineFile(String location, String name) throws IOException {
        if (!RelativePaths.isSafe(name) || name.contains("/")) {
            throw new IOException("invalid engine file name: " + name);
        }
        return directory(location).resolve(name);
    }

    // the engine mounts only its own run's directory (spec 0011), so it needs no credentials
    @Override
    public Optional<RunCredentials> issueRunCredentials(String location, Duration lifetime) {
        return Optional.empty();
    }

    @Override
    public void removeIntermediates(String location) {
        for (String name : INTERMEDIATE_DIRECTORIES) {
            try {
                deleteRecursively(directory(location).resolve(name));
            } catch (IOException exception) {
                // files the engine's containers wrote may not be deletable by core-api on every platform (e.g.
                // root-owned on linux), which is logged rather than failing the run
                log.warn("could not fully remove {}/{}: {}", location, name, exception.toString());
            }
        }
    }

    // a file can be created in the storage root. a missing root is unhealthy, not created: setup creates it
    @Override
    public Optional<String> check() {
        if (!Files.isDirectory(root)) {
            return Optional.of("storage root " + root + " does not exist");
        }
        Path probe = null;
        try {
            probe = Files.createTempFile(root, ".refcloud-health-", ".tmp");
            return Optional.empty();
        } catch (IOException exception) {
            return Optional.of("storage root " + root + " is not writable: " + exception.getMessage());
        } finally {
            if (probe != null) {
                try {
                    Files.deleteIfExists(probe);
                } catch (IOException exception) {
                    log.debug("could not delete health probe {}: {}", probe, exception.toString());
                }
            }
        }
    }

    // resolves a relative path (a leading slash is ignored) strictly inside root: no "." or ".." segments, and (after
    // symlinks are followed) still inside root. empty if it isn't, or isn't a regular file
    static Optional<Path> resolveInside(Path root, String relative) {
        String path = RelativePaths.stripLeadingSlash(relative);
        if (!RelativePaths.isSafe(path)) {
            return Optional.empty();
        }
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path candidate = normalizedRoot.resolve(path).normalize();
            if (!candidate.startsWith(normalizedRoot) || !Files.isRegularFile(candidate)) {
                return Optional.empty();
            }
            Path real = candidate.toRealPath();
            return real.startsWith(normalizedRoot.toRealPath()) ? Optional.of(real) : Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    static void deleteRecursively(Path directory) throws IOException {
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

    // a run on this storage has a path for its location
    private static Path directory(String location) {
        if (S3Location.isS3(location)) {
            throw new IllegalStateException("run location " + location + " is on S3, not a shared filesystem");
        }
        return Path.of(location);
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            return 0;
        }
    }
}
