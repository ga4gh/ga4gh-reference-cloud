package org.ga4gh.refcloud.api.compute.engine;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputePlatformType;
import org.ga4gh.refcloud.api.compute.run.ComputeRun;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

public class NextflowKubernetesEngineTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private static final String RUN_ID = "0b6f2c1e-8d1a-4c55-9a51-3f1e2d4c5b6a";

    private static final String RUN_DIRECTORY = "/data/refcloud-compute/refcloud/runs/" + RUN_ID;

    private static final ComputeConfig.Engine ENGINE = new ComputeConfig.Engine(
            null,
            "26.10.5",
            "1.5.0",
            "refcloud-nextflow:v1",
            ComputeConfig.ImagePullPolicy.NEVER,
            "refcloud-compute",
            2,
            new BigDecimal("1.5"));

    private static final ComputeConfig.Connection CONNECTION =
            new ComputeConfig.Connection(ComputePlatformType.NEXTFLOW_FUNNEL_KUBERNETES, ENGINE, null, null, null);

    private static ComputeRun run() {
        return ComputeRun.builder()
                .id(RUN_ID)
                .computePlatformId("nextflow-demo-kubernetes")
                .runDirectory(RUN_DIRECTORY)
                .workflowPath(RUN_DIRECTORY + "/workflow/main.nf")
                .engineProfiles(List.of("test"))
                .engineEnv(Map.of("NXF_SYNTAX_PARSER", "v1"))
                .maxDurationSeconds(1800)
                .build();
    }

    private static JsonNode job() {
        return NextflowKubernetesEngine.job(run(), CONNECTION, Duration.ofMinutes(2));
    }

    @Test
    public void jobIsNamedAndLabelledByTheRun() {
        JsonNode job = job();

        assertEquals(job.at("/metadata/name").asString(), "nextflow-" + RUN_ID);
        for (String labels : List.of("/metadata/labels", "/spec/template/metadata/labels")) {
            assertEquals(job.at(labels + "/refcloud.ga4gh.org~1run-id").asString(), RUN_ID);
            assertEquals(
                    job.at(labels + "/refcloud.ga4gh.org~1compute-platform-id").asString(), "nextflow-demo-kubernetes");
            assertEquals(job.at(labels + "/app.kubernetes.io~1name").asString(), "refcloud-nextflow");
        }
    }

    @Test
    public void jobRunsOnceAndOutlastsTheRunsOwnDeadline() {
        JsonNode spec = job().path("spec");

        assertEquals(spec.path("backoffLimit").asInt(), 0);
        // max duration + cancel grace period + margin: core-api stops overdue runs itself
        assertEquals(spec.path("activeDeadlineSeconds").asLong(), 1800 + 120 + 300);
        assertEquals(spec.at("/template/spec/restartPolicy").asString(), "Never");
        assertEquals(spec.at("/template/spec/terminationGracePeriodSeconds").asLong(), 120);
    }

    @Test
    public void podHasNoKubernetesAccessOrPrivileges() {
        JsonNode pod = job().at("/spec/template/spec");
        JsonNode container = pod.at("/containers/0");

        assertFalse(pod.path("automountServiceAccountToken").asBoolean(true));
        assertFalse(pod.path("enableServiceLinks").asBoolean(true));
        assertTrue(pod.at("/securityContext/runAsNonRoot").asBoolean());
        assertEquals(pod.at("/securityContext/runAsUser").asLong(), 10001);
        assertEquals(pod.at("/securityContext/runAsGroup").asLong(), 10001);
        assertEquals(pod.at("/securityContext/seccompProfile/type").asString(), "RuntimeDefault");
        assertFalse(container.at("/securityContext/allowPrivilegeEscalation").asBoolean(true));
        assertTrue(container.at("/securityContext/readOnlyRootFilesystem").asBoolean());
        assertEquals(container.at("/securityContext/capabilities/drop/0").asString(), "ALL");
    }

    @Test
    public void containerRunsTheLauncherWithTheImagesNextflowWrapper() {
        JsonNode container = job().at("/spec/template/spec/containers/0");
        List<String> command = strings(container.path("command"));

        assertEquals(container.path("image").asString(), "refcloud-nextflow:v1");
        assertEquals(container.path("imagePullPolicy").asString(), "Never");
        assertEquals(container.path("workingDir").asString(), RUN_DIRECTORY);
        assertEquals(
                command.subList(0, 4),
                List.of("sh", RUN_DIRECTORY + "/.refcloud-launcher.sh", RUN_DIRECTORY, "refcloud-nextflow"));
        // stage 1's nextflow arguments
        assertEquals(command.subList(4, 6), List.of("-log", RUN_DIRECTORY + "/nextflow.log"));
        assertTrue(command.contains("-profile"));
        assertEquals(
                command.subList(command.size() - 2, command.size()), List.of("--outdir", RUN_DIRECTORY + "/outputs"));
    }

    @Test
    public void containerMountsOnlyItsRunDirectoryAtTheSamePath() {
        JsonNode pod = job().at("/spec/template/spec");

        assertEquals(pod.at("/volumes/0/hostPath/path").asString(), RUN_DIRECTORY);
        assertEquals(pod.at("/volumes/0/hostPath/type").asString(), "Directory");
        assertEquals(pod.at("/containers/0/volumeMounts/0/mountPath").asString(), RUN_DIRECTORY);
        assertEquals(pod.at("/containers/0/volumeMounts/1/mountPath").asString(), "/tmp");
        assertTrue(pod.at("/volumes/1/emptyDir").isObject());
    }

    @Test
    public void limitsEqualRequests() {
        JsonNode resources = job().at("/spec/template/spec/containers/0/resources");

        for (String kind : List.of("requests", "limits")) {
            assertEquals(resources.at("/" + kind + "/cpu").asString(), "2");
            assertEquals(resources.at("/" + kind + "/memory").asString(), "1.5G");
        }
    }

    @Test
    public void tokenComesFromTheRunsSecretNeverTheSpec() {
        ArrayNode env = (ArrayNode) job().at("/spec/template/spec/containers/0/env");
        JsonNode token = null;
        for (JsonNode variable : env) {
            if (variable.path("name").asString().equals("REFCLOUD_TES_TOKEN")) {
                token = variable;
            }
        }

        assertTrue(token != null);
        assertFalse(token.has("value"));
        assertEquals(token.at("/valueFrom/secretKeyRef/name").asString(), "nextflow-" + RUN_ID + "-tes-token");
        assertEquals(token.at("/valueFrom/secretKeyRef/key").asString(), "token");

        JsonNode secret = NextflowKubernetesEngine.secret(run(), "rcrun_secret");
        assertEquals(secret.at("/metadata/name").asString(), "nextflow-" + RUN_ID + "-tes-token");
        assertEquals(secret.at("/stringData/token").asString(), "rcrun_secret");
        assertEquals(secret.at("/metadata/labels/refcloud.ga4gh.org~1run-id").asString(), RUN_ID);
    }

    @Test
    public void environmentPinsVersionsAndRunsOffline() {
        Map<String, String> environment = NextflowKubernetesEngine.environment(run(), ENGINE);

        assertEquals(environment.get("NXF_VER"), "26.10.5");
        assertEquals(environment.get("NXF_PLUGINS_DEFAULT"), "nf-ga4gh@1.5.0");
        assertEquals(environment.get("NXF_OFFLINE"), "true");
        assertEquals(environment.get("NXF_HOME"), "/tmp/nextflow-home");
        assertEquals(environment.get("NXF_PLUGINS_DIR"), "/opt/nextflow/plugins");
        // 62.5% of 1.5 GB
        assertEquals(environment.get("NXF_OPTS"), "-Xmx937m");
        // the allowlist entry's own variables are kept
        assertEquals(environment.get("NXF_SYNTAX_PARSER"), "v1");
        assertFalse(environment.containsKey("REFCLOUD_TES_TOKEN"));
    }

    @Test
    public void everythingTheJobSetsIsReserved() {
        NextflowKubernetesEngine engine = new NextflowKubernetesEngine(null, null, null);
        Map<String, String> environment = NextflowKubernetesEngine.environment(run(), ENGINE);

        for (String name : environment.keySet()) {
            if (!name.equals("NXF_SYNTAX_PARSER") && !name.equals("NXF_ANSI_LOG")) {
                assertTrue(engine.isReservedEnvironmentVariable(name), name);
            }
        }
        assertTrue(engine.isReservedEnvironmentVariable("REFCLOUD_TES_TOKEN"));
        assertFalse(engine.isReservedEnvironmentVariable("NXF_SYNTAX_PARSER"));
    }

    // status from the Job and pods, when the run directory has no exit code yet

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    @Test
    public void runningJobIsAlive() throws Exception {
        assertEquals(status(job("{}"), pods(pod(NOW.minusSeconds(30), null))), new EngineStatus.Alive());
    }

    @Test
    public void deletedJobWhosePodIsShuttingDownIsAlive() throws Exception {
        assertEquals(status(Optional.empty(), pods(pod(NOW.minusSeconds(30), null))), new EngineStatus.Alive());
    }

    @Test
    public void goneJobAndPodIsLost() throws Exception {
        assertTrue(status(Optional.empty(), pods()) instanceof EngineStatus.Lost);
    }

    @Test
    public void failedJobIsLostWithItsReason() throws Exception {
        EngineStatus status = status(
                job("{\"conditions\":[{\"type\":\"Failed\",\"status\":\"True\",\"reason\":\"DeadlineExceeded\","
                        + "\"message\":\"Job was active longer than specified deadline\"}]}"),
                pods());

        assertTrue(((EngineStatus.Lost) status).reason().contains("DeadlineExceeded"), status.toString());
    }

    @Test
    public void podThatCantPullItsImageIsLostAfterTheGracePeriod() throws Exception {
        JsonNode stuck = pod(NOW.minus(Duration.ofMinutes(3)), "ErrImageNeverPull");
        JsonNode justStarted = pod(NOW.minusSeconds(30), "ErrImageNeverPull");

        EngineStatus status = status(job("{}"), pods(stuck));
        assertTrue(((EngineStatus.Lost) status).reason().contains("ErrImageNeverPull"), status.toString());
        assertEquals(status(job("{}"), pods(justStarted)), new EngineStatus.Alive());
    }

    @Test
    public void podPullingItsImageIsAlive() throws Exception {
        assertEquals(
                status(job("{}"), pods(pod(NOW.minus(Duration.ofMinutes(10)), "ContainerCreating"))),
                new EngineStatus.Alive());
    }

    @Test
    public void succeededJobWithoutAnExitCodeIsLost() throws Exception {
        assertTrue(status(job("{\"succeeded\":1}"), pods()) instanceof EngineStatus.Lost);
    }

    @Test
    public void namesAreValidKubernetesNames() {
        String name = NextflowKubernetesEngine.secretName(run());

        assertTrue(name.matches(ComputeConfig.KUBERNETES_NAME_PATTERN), name);
        assertTrue(NextflowKubernetesEngine.jobName(run()).matches(ComputeConfig.KUBERNETES_NAME_PATTERN));
        assertTrue(name.length() <= 63);
    }

    @Test
    public void tesEndpointUsesCoreApisAddressSoTheJobNeedsNoDns() throws Exception {
        String endpoint = "http://refcloud-api.default.svc:8080/ga4gh/tes/nextflow-demo-kubernetes";

        assertEquals(
                NextflowKubernetesEngine.withResolvedHost(
                        endpoint, host -> InetAddress.getByAddress(host, new byte[] {10, 96, 1, 2})),
                "http://10.96.1.2:8080/ga4gh/tes/nextflow-demo-kubernetes");
        // an IPv6 cluster
        assertEquals(
                NextflowKubernetesEngine.withResolvedHost(endpoint, host -> InetAddress.getByName("fd00::1")),
                "http://[fd00:0:0:0:0:0:0:1]:8080/ga4gh/tes/nextflow-demo-kubernetes");
    }

    @Test
    public void unresolvableCoreApiFailsTheLaunch() {
        expectThrows(
                UnknownHostException.class,
                () -> NextflowKubernetesEngine.withResolvedHost("http://nowhere:8080/x", host -> {
                    throw new UnknownHostException(host);
                }));
    }

    @Test
    public void heapLeavesRoomForTheJvm() {
        assertEquals(NextflowKubernetesEngine.heapMegabytes(BigDecimal.ONE), 625);
        assertEquals(NextflowKubernetesEngine.heapMegabytes(new BigDecimal("2")), 1250);
    }

    @Test
    public void engineSettingsMustMatchTheType() {
        ComputeConfig.Engine docker =
                new ComputeConfig.Engine("nextflow", "26.10.5", "1.5.0", null, null, null, null, null);
        ComputeConfig.Engine noNamespace =
                new ComputeConfig.Engine(null, "26.10.5", "1.5.0", "image:v1", null, null, null, null);

        assertTrue(connection(ComputePlatformType.NEXTFLOW_FUNNEL_KUBERNETES, ENGINE)
                .isEngineValidForType());
        assertTrue(
                connection(ComputePlatformType.NEXTFLOW_FUNNEL_DOCKER, docker).isEngineValidForType());
        assertFalse(connection(ComputePlatformType.NEXTFLOW_FUNNEL_KUBERNETES, docker)
                .isEngineValidForType());
        assertFalse(connection(ComputePlatformType.NEXTFLOW_FUNNEL_KUBERNETES, noNamespace)
                .isEngineValidForType());
        assertFalse(
                connection(ComputePlatformType.NEXTFLOW_FUNNEL_DOCKER, ENGINE).isEngineValidForType());
    }

    @Test
    public void engineDefaultsSizeTheJob() {
        ComputeConfig.Engine defaults =
                new ComputeConfig.Engine(null, "26.10.5", "1.5.0", "image:v1", null, "ns", null, null);

        assertEquals(defaults.cpuCores(), Integer.valueOf(1));
        assertEquals(defaults.memoryGb(), BigDecimal.ONE);
        assertEquals(defaults.imagePullPolicy(), ComputeConfig.ImagePullPolicy.IF_NOT_PRESENT);
    }

    private static ComputeConfig.Connection connection(ComputePlatformType type, ComputeConfig.Engine engine) {
        return new ComputeConfig.Connection(type, engine, null, null, null);
    }

    private static EngineStatus status(Optional<JsonNode> job, JsonNode pods) {
        return NextflowKubernetesEngine.status(job, pods, NOW);
    }

    private static Optional<JsonNode> job(String status) throws Exception {
        return Optional.of(JSON.readTree("{\"metadata\":{\"name\":\"nextflow-x\"},\"status\":" + status + "}"));
    }

    private static JsonNode pod(Instant created, String waitingReason) throws Exception {
        String state = waitingReason == null
                ? "{\"running\":{}}"
                : "{\"waiting\":{\"reason\":\"" + waitingReason + "\",\"message\":\"details\"}}";
        return JSON.readTree("{\"metadata\":{\"name\":\"nextflow-x-abcde\",\"creationTimestamp\":\"" + created
                + "\"},\"status\":{\"containerStatuses\":[{\"name\":\"nextflow\",\"state\":" + state + "}]}}");
    }

    private static JsonNode pods(JsonNode... pods) {
        ArrayNode items = JsonNodeFactory.instance.arrayNode();
        for (JsonNode pod : pods) {
            items.add(pod);
        }
        return items;
    }

    private static List<String> strings(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }
}
