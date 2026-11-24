package org.ga4gh.refcloud.api.compute.health;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputeHealthConfig;
import org.ga4gh.refcloud.api.compute.ComputePlatformType;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

// the three connection checks against a local http server (the executor), fake nextflow launcher scripts (the
// engine) and temporary directories (storage)
public class ConnectionCheckerTest {

    private final ConnectionChecker checker = new ConnectionChecker(
            new ComputeHealthConfig(Duration.ofSeconds(60), Duration.ofSeconds(2), Duration.ofSeconds(10)));

    private HttpServer executor;

    private Path workDir;

    @BeforeClass
    public void start() throws IOException {
        executor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor.createContext("/ga4gh/tes/v1/service-info", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            int status = auth == null || auth.equals("Bearer good") ? 200 : 401;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        executor.start();
        workDir = Files.createTempDirectory("connection-checker-test");
    }

    @AfterClass
    public void stop() throws IOException {
        executor.stop(0);
        try (Stream<Path> paths = Files.walk(workDir)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private URI executorUrl() {
        return URI.create("http://127.0.0.1:" + executor.getAddress().getPort());
    }

    // a fake launcher that prints what nextflow -version prints for the version in NXF_VER
    private Path fakeNextflow(String body) throws IOException {
        Path script = Files.createTempFile(workDir, "nextflow", ".sh");
        Files.writeString(script, "#!/bin/sh\n" + body + "\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }

    private static ComputeConfig.Engine engine(Path executable, String version) {
        return new ComputeConfig.Engine(executable.toString(), version, "1.5.0");
    }

    @Test
    public void executorRespondingIsHealthy() {
        assertEquals(checker.checkExecutor(new ComputeConfig.Executor(executorUrl(), null)), Optional.empty());
    }

    @Test
    public void executorWithTrailingSlashIsHealthy() {
        URI withSlash = URI.create(executorUrl() + "/");
        assertEquals(checker.checkExecutor(new ComputeConfig.Executor(withSlash, null)), Optional.empty());
    }

    @Test
    public void executorErrorStatusIsUnhealthy() {
        // credentialEnv names PATH, which is always set: its value is sent as the token, which the fake executor
        // rejects
        Optional<String> failure = checker.checkExecutor(new ComputeConfig.Executor(executorUrl(), "PATH"));
        assertTrue(failure.orElseThrow().contains("returned HTTP 401"), failure.toString());
    }

    @Test
    public void unsetCredentialVariableIsUnhealthy() {
        Optional<String> failure =
                checker.checkExecutor(new ComputeConfig.Executor(executorUrl(), "REFCLOUD_TEST_UNSET_CREDENTIAL"));
        assertTrue(failure.orElseThrow().contains("is not set"), failure.toString());
    }

    @Test
    public void unreachableExecutorIsUnhealthy() {
        Optional<String> failure =
                checker.checkExecutor(new ComputeConfig.Executor(URI.create("http://127.0.0.1:1"), null));
        assertTrue(failure.orElseThrow().contains("unreachable"), failure.toString());
    }

    @Test
    public void pinnedNextflowVersionIsHealthy() throws IOException {
        Path nextflow = fakeNextflow("echo \"      version $NXF_VER build 14654\"");
        assertEquals(checker.checkEngine(engine(nextflow, "26.10.5")), Optional.empty());
    }

    // a self-contained build (e.g. Homebrew's nextflow) ignores NXF_VER and runs its own version
    @Test
    public void nextflowIgnoringThePinnedVersionIsUnhealthy() throws IOException {
        Path nextflow = fakeNextflow("echo '      version 26.10.6 build 0'");
        Optional<String> failure = checker.checkEngine(engine(nextflow, "26.10.5"));
        assertTrue(
                failure.orElseThrow().contains("ran version 26.10.6 instead of the pinned 26.10.5"),
                failure.toString());
        assertTrue(failure.orElseThrow().contains("ignores NXF_VER"), failure.toString());
    }

    @Test
    public void nextflowWithoutAVersionLineIsUnhealthy() throws IOException {
        Path nextflow = fakeNextflow("echo 'something else'");
        Optional<String> failure = checker.checkEngine(engine(nextflow, "26.10.5"));
        assertTrue(
                failure.orElseThrow().contains("did not report version 26.10.5: something else"), failure.toString());
    }

    @Test
    public void failingNextflowIsUnhealthy() throws IOException {
        Path nextflow = fakeNextflow("echo 'cannot download nextflow' >&2; exit 1");
        Optional<String> failure = checker.checkEngine(engine(nextflow, "26.10.5"));
        assertTrue(failure.orElseThrow().contains("exited with 1: cannot download nextflow"), failure.toString());
    }

    @Test
    public void missingNextflowIsUnhealthy() {
        Optional<String> failure = checker.checkEngine(engine(workDir.resolve("no-such-nextflow"), "26.10.5"));
        assertTrue(failure.orElseThrow().contains("could not be run"), failure.toString());
    }

    @Test
    public void writableStorageRootIsHealthyAndLeavesNoFiles() throws IOException {
        Path root = Files.createTempDirectory(workDir, "storage");
        assertEquals(checker.checkStorage(root), Optional.empty());
        try (Stream<Path> files = Files.list(root)) {
            assertEquals(files.count(), 0L);
        }
    }

    @Test
    public void missingStorageRootIsUnhealthyAndNotCreated() {
        Path root = workDir.resolve("no-such-storage");
        Optional<String> failure = checker.checkStorage(root);
        assertTrue(failure.orElseThrow().contains("does not exist"), failure.toString());
        assertFalse(Files.exists(root));
    }

    @Test
    public void readOnlyStorageRootIsUnhealthy() throws IOException {
        Path root = Files.createTempDirectory(workDir, "read-only");
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("r-x------"));
        try {
            assertTrue(checker.checkStorage(root).orElseThrow().contains("not writable"));
        } finally {
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    public void allChecksReportEveryFailure() throws IOException {
        ComputeConfig.Connection connection = new ComputeConfig.Connection(
                ComputePlatformType.NEXTFLOW_FUNNEL_DOCKER,
                engine(workDir.resolve("no-such-nextflow"), "26.10.5"),
                new ComputeConfig.Executor(URI.create("http://127.0.0.1:1"), null),
                new ComputeConfig.Storage(ComputeConfig.StorageType.SHARED_FILESYSTEM, "x"),
                new ComputeConfig.Limits(
                        1,
                        1,
                        1,
                        BigDecimal.ONE,
                        1,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        60,
                        null,
                        1,
                        BigDecimal.ONE,
                        BigDecimal.TEN));

        List<String> failures = checker.check(connection, workDir.resolve("no-such-storage"));

        assertEquals(failures.size(), 3, failures.toString());
    }
}
