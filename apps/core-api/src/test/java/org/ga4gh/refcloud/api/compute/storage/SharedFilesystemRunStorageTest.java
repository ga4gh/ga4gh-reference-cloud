package org.ga4gh.refcloud.api.compute.storage;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SharedFilesystemRunStorageTest {

    private Path directory;

    private SharedFilesystemRunStorage storage;

    @BeforeMethod
    public void setUp() throws IOException {
        directory = Files.createTempDirectory("shared-filesystem-run-storage-test");
        storage = new SharedFilesystemRunStorage(directory);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws IOException {
        SharedFilesystemRunStorage.deleteRecursively(directory);
    }

    @Test
    public void runDirectoryIsCreatedWithItsLayout() throws IOException {
        String location = storage.allocate("r1");

        assertEquals(location, directory.resolve("runs/r1").toString());
        for (String child : List.of("inputs", "work", "outputs")) {
            assertTrue(Files.isDirectory(Path.of(location).resolve(child)), child);
        }
    }

    @Test
    public void workflowStagingIsTheRunsWorkflowDirectoryStartedClean() throws IOException {
        String location = storage.allocate("r1");
        Files.createDirectories(Path.of(location).resolve("workflow"));
        Files.writeString(Path.of(location).resolve("workflow/stale.nf"), "from a run that was interrupted");

        Path staging = storage.workflowStaging(location);
        storage.publishWorkflow(location, staging);

        assertEquals(staging, Path.of(location).resolve("workflow"));
        try (Stream<Path> files = Files.list(staging)) {
            assertEquals(files.count(), 0L);
        }
    }

    @Test
    public void inputIsStagedOnlyWhenItsSizeIsRight() throws IOException {
        String location = storage.allocate("r1");

        String staged = storage.stageInput(
                location, "obj1/x.bai", new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), 3L);
        IOException wrongSize = expectThrows(
                IOException.class,
                () -> storage.stageInput(
                        location, "obj2/y.bai", new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), 4L));

        assertEquals(staged, Path.of(location).resolve("inputs/obj1/x.bai").toString());
        assertEquals(Files.readString(Path.of(staged)), "abc");
        assertEquals(wrongSize.getMessage(), "downloaded 3 bytes of y.bai, expected 4");
        try (Stream<Path> files = Files.list(Path.of(location).resolve("inputs/obj2"))) {
            assertEquals(files.count(), 0L);
        }
    }

    @Test
    public void outputsAreListedAndOpenedByRelativePath() throws IOException {
        String location = storage.allocate("r1");
        Path outputs = Path.of(location).resolve("outputs");
        Files.createDirectories(outputs.resolve("qc"));
        Files.writeString(outputs.resolve("report.json"), "{}");
        Files.writeString(outputs.resolve("qc/summary.txt"), "ok!");

        assertEquals(
                storage.listOutputs(location),
                List.of(new StoredFile("qc/summary.txt", 3), new StoredFile("report.json", 2)));
        assertEquals(
                storage.openOutput(location, "/qc/summary.txt").orElseThrow().contentLength(), 3);
        assertEquals(storage.openOutput(location, "../work/x"), Optional.empty());
    }

    @Test
    public void endedRunKeepsOnlyItsOutputsAndLogs() throws IOException {
        String location = storage.allocate("r1");
        Path run = Path.of(location);
        for (String name : List.of("workflow/main.nf", "inputs/obj1/x.bai", "work/ab/.command.sh", ".nextflow/cache")) {
            Files.createDirectories(run.resolve(name).getParent());
            Files.writeString(run.resolve(name), "x");
        }
        Files.writeString(run.resolve("outputs/report.json"), "{}");
        Files.writeString(run.resolve("engine.stdout.log"), "log");

        storage.removeIntermediates(location);

        try (Stream<Path> files = Files.list(run)) {
            assertEquals(
                    files.map(path -> path.getFileName().toString()).sorted().toList(),
                    List.of("engine.stdout.log", "outputs"));
        }
        assertEquals(
                storage.openEngineLog(location, "engine.stdout.log")
                        .orElseThrow()
                        .contentLength(),
                3);
        assertEquals(storage.openEngineLog(location, "engine.stderr.log"), Optional.empty());
    }

    @Test
    public void runOnS3IsRefused() {
        expectThrows(IllegalStateException.class, () -> storage.listOutputs("s3://bucket/runs/r1"));
    }

    @Test
    public void outputPathsResolveStrictlyInsideOutputs() throws IOException {
        Path outputs = Files.createDirectories(directory.resolve("outputs"));
        Files.createDirectories(outputs.resolve("report"));
        Files.writeString(outputs.resolve("report/summary.txt"), "ok");
        Files.writeString(directory.resolve("secret.txt"), "no");
        Files.createSymbolicLink(outputs.resolve("escape.txt"), directory.resolve("secret.txt"));
        Files.createSymbolicLink(outputs.resolve("inside.txt"), outputs.resolve("report/summary.txt"));

        assertEquals(
                SharedFilesystemRunStorage.resolveInside(outputs, "/report/summary.txt"),
                Optional.of(outputs.resolve("report/summary.txt").toRealPath()));
        assertEquals(
                SharedFilesystemRunStorage.resolveInside(outputs, "report/summary.txt"),
                Optional.of(outputs.resolve("report/summary.txt").toRealPath()));
        // a symlink that stays inside outputs is fine
        assertTrue(
                SharedFilesystemRunStorage.resolveInside(outputs, "inside.txt").isPresent());

        for (String rejected : new String[] {
            "../secret.txt",
            "/report/../../secret.txt",
            "report/./summary.txt",
            "//etc/passwd",
            "report//summary.txt",
            "escape.txt",
            "report",
            "missing.txt",
            "",
            "/",
            "report\\summary.txt"
        }) {
            assertEquals(SharedFilesystemRunStorage.resolveInside(outputs, rejected), Optional.empty(), rejected);
        }
    }

    @Test
    public void writableStorageRootIsHealthyAndLeavesNoFiles() throws IOException {
        assertEquals(storage.check(), Optional.empty());
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(files.count(), 0L);
        }
    }

    @Test
    public void missingStorageRootIsUnhealthyAndNotCreated() {
        Path root = directory.resolve("no-such-storage");
        Optional<String> failure = new SharedFilesystemRunStorage(root).check();
        assertTrue(failure.orElseThrow().contains("does not exist"), failure.toString());
        assertFalse(Files.exists(root));
    }

    @Test
    public void readOnlyStorageRootIsUnhealthy() throws IOException {
        Path root = Files.createDirectories(directory.resolve("read-only"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("r-x------"));
        try {
            assertTrue(
                    new SharedFilesystemRunStorage(root).check().orElseThrow().contains("not writable"));
        } finally {
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        }
    }
}
