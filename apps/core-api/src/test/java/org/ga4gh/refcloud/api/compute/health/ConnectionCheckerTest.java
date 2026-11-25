package org.ga4gh.refcloud.api.compute.health;

import static org.testng.Assert.assertEquals;
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
import java.util.Set;
import java.util.stream.Stream;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputeHealthConfig;
import org.ga4gh.refcloud.api.compute.ComputePlatformType;
import org.ga4gh.refcloud.api.compute.kubernetes.KubernetesClient;
import org.ga4gh.refcloud.api.compute.kubernetes.KubernetesPermission;
import org.ga4gh.refcloud.api.compute.storage.SharedFilesystemRunStorage;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

// the three connection checks against a local http server (the executor), fake nextflow launcher scripts (the
// engine) and temporary directories (storage)
public class ConnectionCheckerTest {

    private static final ComputeHealthConfig HEALTH_CONFIG =
            new ComputeHealthConfig(Duration.ofSeconds(60), Duration.ofSeconds(2), Duration.ofSeconds(10));

    // nextflow-funnel-docker checks never call kubernetes
    private final ConnectionChecker checker = new ConnectionChecker(HEALTH_CONFIG, null);

    // a kubernetes API that allows everything except the denied permissions, or fails every call
    private static KubernetesClient kubernetes(Set<KubernetesPermission> denied, boolean unreachable) {
        return new KubernetesClient(JsonMapper.builder().build()) {
            @Override
            public boolean isAllowed(String namespace, KubernetesPermission permission) throws IOException {
                if (unreachable) {
                    throw new IOException("connection refused");
                }
                return namespace.equals("refcloud-compute") && !denied.contains(permission);
            }
        };
    }

    private static final ComputeConfig.Engine KUBERNETES_ENGINE = new ComputeConfig.Engine(
            null, "26.10.5", "1.5.0", "refcloud-nextflow:v1", null, "refcloud-compute", null, null, null, null, null);

    @Test
    public void kubernetesEngineIsHealthyWhenEverythingTheEngineDoesIsAllowed() {
        ConnectionChecker kubernetesChecker = new ConnectionChecker(HEALTH_CONFIG, kubernetes(Set.of(), false));

        assertEquals(kubernetesChecker.checkKubernetesEngine(KUBERNETES_ENGINE), Optional.empty());
    }

    @Test
    public void kubernetesEngineNamesWhatIsDenied() {
        Set<KubernetesPermission> denied = Set.of(
                new KubernetesPermission("batch", "jobs", "create"), new KubernetesPermission("", "secrets", "create"));
        ConnectionChecker kubernetesChecker = new ConnectionChecker(HEALTH_CONFIG, kubernetes(denied, false));

        String failure =
                kubernetesChecker.checkKubernetesEngine(KUBERNETES_ENGINE).orElseThrow();
        assertTrue(failure.contains("create jobs.batch"), failure);
        assertTrue(failure.contains("create secrets"), failure);
        assertTrue(failure.contains("refcloud-compute"), failure);
    }

    @Test
    public void kubernetesEngineIsUnhealthyWhenTheApiCantBeReached() {
        ConnectionChecker kubernetesChecker = new ConnectionChecker(HEALTH_CONFIG, kubernetes(Set.of(), true));

        String failure =
                kubernetesChecker.checkKubernetesEngine(KUBERNETES_ENGINE).orElseThrow();
        assertTrue(failure.contains("connection refused"), failure);
    }

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
        return new ComputeConfig.Engine(
                executable.toString(), version, "1.5.0", null, null, null, null, null, null, null, null);
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
    public void allChecksReportEveryFailure() throws IOException {
        ComputeConfig.Connection connection = new ComputeConfig.Connection(
                ComputePlatformType.NEXTFLOW_FUNNEL_DOCKER,
                engine(workDir.resolve("no-such-nextflow"), "26.10.5"),
                new ComputeConfig.Executor(URI.create("http://127.0.0.1:1"), null),
                new ComputeConfig.Storage(
                        ComputeConfig.StorageType.SHARED_FILESYSTEM,
                        "x",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
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

        List<String> failures =
                checker.check(connection, new SharedFilesystemRunStorage(workDir.resolve("no-such-storage")));

        assertEquals(failures.size(), 3, failures.toString());
    }
}
