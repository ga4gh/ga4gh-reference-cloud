package org.ga4gh.refcloud.api.compute.engine;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

// launches real (tiny) processes through the run launcher script, and follows them through the run directory's files
public class HostProcessRunnerTest {

    private final HostProcessRunner runner = new HostProcessRunner();

    private Path runDirectory;

    @BeforeMethod
    public void createRunDirectory() throws IOException {
        runDirectory = Files.createTempDirectory("host-process-runner-test");
    }

    @AfterMethod
    public void deleteRunDirectory() throws IOException {
        try (Stream<Path> paths = Files.walk(runDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private EngineProcess launch(String script) throws IOException {
        return runner.launch(
                runDirectory, List.of("sh", "-c", script), Map.of("REFCLOUD_TEST", "hello"), Duration.ofSeconds(10));
    }

    private EngineStatus waitForExit(EngineProcess process) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            EngineStatus status = runner.status(runDirectory, process);
            if (!(status instanceof EngineStatus.Alive)) {
                return status;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("still running");
    }

    @Test
    public void exitCodeIsRecordedWithOutputAndEnvironment() throws Exception {
        EngineProcess process = launch("echo \"$REFCLOUD_TEST\"; echo oops >&2; exit 3");

        assertTrue(process.pid() > 0);
        assertEquals(waitForExit(process), new EngineStatus.Exited(3));
        assertEquals(Files.readString(runDirectory.resolve("engine.stdout.log")).strip(), "hello");
        assertEquals(Files.readString(runDirectory.resolve("engine.stderr.log")).strip(), "oops");
    }

    @Test
    public void runningProcessIsAliveAndCanBeTerminated() throws Exception {
        EngineProcess process = launch("sleep 30");
        assertNotNull(process.startedAt(), "the start time identifies the process");
        assertEquals(runner.status(runDirectory, process), new EngineStatus.Alive());

        runner.signal(runDirectory, process, false);

        // SIGTERM: 128 + 15
        assertEquals(waitForExit(process), new EngineStatus.Exited(143));
    }

    @Test
    public void forcedTerminationKills() throws Exception {
        EngineProcess process = launch("trap '' TERM; sleep 30");

        runner.signal(runDirectory, process, true);

        // SIGKILL: 128 + 9
        assertEquals(waitForExit(process), new EngineStatus.Exited(137));
    }

    @Test
    public void processGoneWithoutAnExitCodeIsLost() {
        // a pid that isn't running, and no exit code file
        EngineProcess gone = new EngineProcess(Integer.MAX_VALUE - 7, Instant.now());

        assertEquals(runner.status(runDirectory, gone), new EngineStatus.Lost());
    }

    @Test
    public void reusedPidIsNotMistakenForTheEngine() throws Exception {
        EngineProcess process = launch("sleep 30");
        try {
            // the same pid, but a different recorded start time: some other process now has it
            EngineProcess stale =
                    new EngineProcess(process.pid(), process.startedAt().minusSeconds(3600));
            assertFalse(runner.find(runDirectory, stale).isPresent());
            // and a signal for it is never sent
            runner.signal(runDirectory, stale, true);
            assertEquals(runner.status(runDirectory, process), new EngineStatus.Alive());
        } finally {
            runner.signal(runDirectory, process, true);
            waitForExit(process);
        }
    }

    @Test
    public void missingEngineExecutableExitsWithCommandNotFound() throws Exception {
        // the launcher forks before the exec fails, so the launch itself succeeds and the engine exits 127
        EngineProcess process =
                runner.launch(runDirectory, List.of("/no/such/engine"), Map.of(), Duration.ofSeconds(5));

        assertEquals(waitForExit(process), new EngineStatus.Exited(127));
    }
}
