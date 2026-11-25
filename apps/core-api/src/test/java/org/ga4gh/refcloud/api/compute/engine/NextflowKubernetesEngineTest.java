package org.ga4gh.refcloud.api.compute.engine;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.io.IOException;
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
import org.ga4gh.refcloud.api.compute.storage.FakeS3Client;
import org.ga4gh.refcloud.api.compute.storage.FakeStsClient;
import org.ga4gh.refcloud.api.compute.storage.RunCredentials;
import org.ga4gh.refcloud.api.compute.storage.RunStorage;
import org.ga4gh.refcloud.api.compute.storage.RunStorages;
import org.ga4gh.refcloud.api.compute.storage.S3RunStorage;
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
            new BigDecimal("1.5"),
            null,
            null,
            null);

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

    // a run on S3 storage (spec 0012): its location is a prefix, and RustFS's endpoint keeps its name, which the Job's
    // /etc/hosts pins to the address core-api resolves it to
    private static final String S3_RUN = "s3://refcloud-compute/ref/runs/" + RUN_ID;

    private static final ComputeConfig.Storage RUSTFS = new ComputeConfig.Storage(
            ComputeConfig.StorageType.S3,
            null,
            "refcloud-compute",
            "ref/",
            "us-east-1",
            java.net.URI.create("http://rustfs.refcloud-compute.svc:9000/"),
            true,
            null,
            null,
            "arn:aws:iam::000000000000:role/refcloud-compute-run",
            43200);

    private static ComputeRun s3Run() {
        ComputeRun run = run();
        run.setRunDirectory(S3_RUN);
        run.setWorkflowPath(S3_RUN + "/workflow");
        return run;
    }

    private static NextflowKubernetesEngine.S3Access s3Access(ComputeConfig.Storage storage) throws IOException {
        return NextflowKubernetesEngine.s3Access(
                        s3Run(), storage, host -> InetAddress.getByAddress(host, new byte[] {10, 96, 0, 9}))
                .orElseThrow();
    }

    @Test
    public void s3RunsReachTheirEndpointByItsPinnedName() throws IOException {
        NextflowKubernetesEngine.S3Access access = s3Access(RUSTFS);

        assertEquals(access.runUrl(), "http://rustfs.refcloud-compute.svc:9000/refcloud-compute/ref/runs/" + RUN_ID);
        assertEquals(
                access.hostAlias(),
                Optional.of(new NextflowKubernetesEngine.HostAlias("10.96.0.9", "rustfs.refcloud-compute.svc")));
        assertEquals(access.files().directory(), "/refcloud/run");
        assertEquals(access.files().workflow(), "/refcloud/run/workflow");
        assertEquals(access.files().workDir(), S3_RUN + "/work");
        assertEquals(access.files().outputs(), S3_RUN + "/outputs");
        assertEquals(
                access.nextflowConfig(),
                "aws.region = 'us-east-1'\n"
                        + "aws.client.endpoint = 'http://rustfs.refcloud-compute.svc:9000'\n"
                        + "aws.client.s3PathStyleAccess = true\n");
        // a run on a shared filesystem has none
        assertTrue(
                NextflowKubernetesEngine.s3Access(run(), RUSTFS, host -> null).isEmpty());
    }

    // on AWS: the bucket's own URL, and nextflow needs only the region
    @Test
    public void s3RunsOnAwsUseTheBucketsUrl() throws IOException {
        ComputeConfig.Storage aws = new ComputeConfig.Storage(
                ComputeConfig.StorageType.S3,
                null,
                "refcloud-compute",
                "ref/",
                "ca-central-1",
                null,
                false,
                null,
                null,
                "arn:aws:iam::123456789012:role/refcloud-compute-run",
                null);

        NextflowKubernetesEngine.S3Access access = s3Access(aws);

        assertEquals(access.runUrl(), "https://refcloud-compute.s3.ca-central-1.amazonaws.com/ref/runs/" + RUN_ID);
        assertEquals(access.nextflowConfig(), "aws.region = 'ca-central-1'\n");
        assertEquals(access.hostAlias(), Optional.empty());
    }

    // through an S3 interface endpoint (spec 0013): its name stays in every URL, since its TLS certificate names it,
    // and the Job's /etc/hosts pins it to one of its addresses. path-style, as Funnel's workers reach it
    @Test
    public void s3RunsThroughAnInterfaceEndpointKeepItsNameForTls() throws IOException {
        ComputeConfig.Storage endpoint = new ComputeConfig.Storage(
                ComputeConfig.StorageType.S3,
                null,
                "refcloud-compute",
                null,
                "us-east-2",
                java.net.URI.create("https://bucket.vpce-0abc-xyz.s3.us-east-2.vpce.amazonaws.com"),
                true,
                null,
                null,
                "arn:aws:iam::123456789012:role/refcloud-compute-run",
                null);
        ComputeRun run = s3Run();
        run.setRunDirectory("s3://refcloud-compute/runs/" + RUN_ID);
        run.setWorkflowPath("s3://refcloud-compute/runs/" + RUN_ID + "/workflow");

        NextflowKubernetesEngine.S3Access access = NextflowKubernetesEngine.s3Access(
                        run, endpoint, host -> InetAddress.getByAddress(host, new byte[] {10, 0, 11, (byte) 250}))
                .orElseThrow();

        assertEquals(
                access.runUrl(),
                "https://bucket.vpce-0abc-xyz.s3.us-east-2.vpce.amazonaws.com/refcloud-compute/runs/" + RUN_ID);
        assertEquals(
                access.hostAlias(),
                Optional.of(new NextflowKubernetesEngine.HostAlias(
                        "10.0.11.250", "bucket.vpce-0abc-xyz.s3.us-east-2.vpce.amazonaws.com")));
        assertEquals(
                access.nextflowConfig(),
                "aws.region = 'us-east-2'\n"
                        + "aws.client.endpoint = 'https://bucket.vpce-0abc-xyz.s3.us-east-2.vpce.amazonaws.com'\n"
                        + "aws.client.s3PathStyleAccess = true\n");
    }

    // an endpoint given as an address needs no /etc/hosts entry, nor a lookup
    @Test
    public void s3EndpointGivenAsAnAddressNeedsNoAlias() throws IOException {
        NextflowKubernetesEngine.Resolver unused = host -> {
            throw new UnknownHostException(host);
        };

        assertEquals(NextflowKubernetesEngine.hostAlias("10.96.0.9", unused), Optional.empty());
        assertEquals(NextflowKubernetesEngine.hostAlias("[fd00::9]", unused), Optional.empty());
        expectThrows(UnknownHostException.class, () -> NextflowKubernetesEngine.hostAlias("rustfs.nowhere", unused));
    }

    @Test
    public void s3JobKeepsTheEngineFilesInItsOwnDirectory() throws IOException {
        JsonNode job =
                NextflowKubernetesEngine.job(s3Run(), CONNECTION, Duration.ofMinutes(2), Optional.of(s3Access(RUSTFS)));
        JsonNode pod = job.at("/spec/template/spec");
        JsonNode container = pod.at("/containers/0");

        // no hostPath: the Job's own emptyDir, so the compute namespace can enforce restricted Pod Security
        assertTrue(pod.at("/volumes/0/emptyDir").isObject());
        assertTrue(pod.at("/volumes/0/hostPath").isMissingNode());
        assertEquals(container.at("/workingDir").asString(), "/refcloud/run");
        List<String> command = new java.util.ArrayList<>();
        container.path("command").forEach(part -> command.add(part.asString()));
        assertEquals(command.subList(0, 3), List.of("refcloud-run-s3", "/refcloud/run", "refcloud-nextflow"));
        assertEquals(command.get(command.indexOf("-work-dir") + 1), S3_RUN + "/work");
        assertEquals(command.get(command.indexOf("--outdir") + 1), S3_RUN + "/outputs");
        assertEquals(command.get(command.indexOf("-c") + 1), "/refcloud/run/refcloud.config");
        assertEquals(command.get(command.indexOf("run") + 1), "/refcloud/run/workflow");

        Map<String, String> values = new java.util.HashMap<>();
        Map<String, String> fromSecret = new java.util.HashMap<>();
        for (JsonNode variable : container.path("env")) {
            if (variable.has("value")) {
                values.put(
                        variable.path("name").asString(), variable.path("value").asString());
            } else {
                fromSecret.put(
                        variable.path("name").asString(),
                        variable.at("/valueFrom/secretKeyRef/key").asString());
            }
        }
        assertEquals(
                values.get("REFCLOUD_RUN_URL"),
                "http://rustfs.refcloud-compute.svc:9000/refcloud-compute/ref/runs/" + RUN_ID);
        // the endpoint's name, pinned: the Job has no DNS
        assertEquals(pod.at("/hostAliases/0/ip").asString(), "10.96.0.9");
        assertEquals(pod.at("/hostAliases/0/hostnames/0").asString(), "rustfs.refcloud-compute.svc");
        assertEquals(pod.path("hostAliases").size(), 1);
        assertEquals(values.get("REFCLOUD_LOG_SYNC_SECONDS"), "15");
        assertEquals(values.get("AWS_REGION"), "us-east-1");
        assertEquals(values.get("AWS_EC2_METADATA_DISABLED"), "true");
        // the token and the credentials only ever from the run's Secret
        assertEquals(
                fromSecret,
                Map.of(
                        "REFCLOUD_TES_TOKEN", "token",
                        "AWS_ACCESS_KEY_ID", "AWS_ACCESS_KEY_ID",
                        "AWS_SECRET_ACCESS_KEY", "AWS_SECRET_ACCESS_KEY",
                        "AWS_SESSION_TOKEN", "AWS_SESSION_TOKEN"));
    }

    // the exit code and the end of nextflow.log come from the run's storage: here, the engine/ objects on S3
    @Test
    public void s3RunsExitCodeAndLogComeFromTheBucket() throws IOException {
        FakeS3Client s3 = new FakeS3Client();
        S3RunStorage storage = new S3RunStorage(RUSTFS, () -> s3, FakeStsClient::new);
        ComputeRun run = s3Run();

        assertEquals(NextflowKubernetesEngine.exitCode(storage, run), Optional.empty());
        s3.objects.put("refcloud-compute/ref/runs/" + RUN_ID + "/engine/engine.exitcode", "1\n".getBytes());
        assertEquals(NextflowKubernetesEngine.exitCode(storage, run), Optional.of(1));

        s3.objects.put(
                "refcloud-compute/ref/runs/" + RUN_ID + "/engine/nextflow.log",
                ("x".repeat(300_000) + "\nSession aborted -- Cause: Unable to connect to"
                                + " http://10.96.0.1:8080/ga4gh/tes/nextflow-demo-kubernetes/v1/tasks/abc\n")
                        .getBytes());
        RunStorages storages = new RunStorages(null, null, null) {
            @Override
            public RunStorage forRun(ComputeRun ignored) {
                return storage;
            }
        };
        NextflowKubernetesEngine engine = new NextflowKubernetesEngine(null, null, null, storages);
        assertTrue(engine.refcloudFailure(run).orElseThrow().contains("lost contact with the refcloud TES"));
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

    // a connection that names no nodes or SELinux level: the Job may run anywhere, but is never moved once running
    @Test
    public void jobWithoutPlacementRunsAnywhereButIsNeverMoved() {
        JsonNode template = job().at("/spec/template");
        JsonNode pod = template.path("spec");

        assertEquals(
                template.at("/metadata/annotations/karpenter.sh~1do-not-disrupt")
                        .asString(),
                "true");
        assertTrue(pod.path("nodeSelector").isMissingNode());
        assertTrue(pod.path("tolerations").isMissingNode());
        assertTrue(pod.at("/securityContext/seLinuxOptions").isMissingNode());
        // and on a shared filesystem, no storage endpoint to pin
        assertTrue(pod.path("hostAliases").isMissingNode());
    }

    // a connection kept to a node pool (spec 0013): the Job goes only there, with the level its task pods share
    @Test
    public void jobRunsOnTheConnectionsNodesWithItsSelinuxLevel() {
        ComputeConfig.Engine placed = new ComputeConfig.Engine(
                null,
                "26.10.5",
                "1.5.0",
                "refcloud-nextflow:v1",
                null,
                "refcloud-compute",
                null,
                null,
                List.of(new ComputeConfig.NodeLabel("refcloud.ga4gh.org/compute", "staging")),
                List.of(
                        new ComputeConfig.Toleration("refcloud.ga4gh.org/compute", "Equal", "staging", "NoSchedule"),
                        new ComputeConfig.Toleration("example.com/any", "Exists", null, null)),
                "s0:c123,c456");
        JsonNode pod = NextflowKubernetesEngine.job(
                        run(),
                        new ComputeConfig.Connection(
                                ComputePlatformType.NEXTFLOW_FUNNEL_KUBERNETES, placed, null, null, null),
                        Duration.ofMinutes(2))
                .at("/spec/template/spec");

        assertEquals(pod.path("nodeSelector").size(), 1);
        assertEquals(pod.at("/nodeSelector/refcloud.ga4gh.org~1compute").asString(), "staging");
        assertEquals(pod.path("tolerations").size(), 2);
        assertEquals(pod.at("/tolerations/0/key").asString(), "refcloud.ga4gh.org/compute");
        assertEquals(pod.at("/tolerations/0/operator").asString(), "Equal");
        assertEquals(pod.at("/tolerations/0/value").asString(), "staging");
        assertEquals(pod.at("/tolerations/0/effect").asString(), "NoSchedule");
        // unset fields are left out, as kubernetes reads them
        assertEquals(pod.at("/tolerations/1/operator").asString(), "Exists");
        assertTrue(pod.at("/tolerations/1/value").isMissingNode());
        assertTrue(pod.at("/tolerations/1/effect").isMissingNode());
        assertEquals(pod.at("/securityContext/seLinuxOptions/level").asString(), "s0:c123,c456");
        assertEquals(pod.at("/securityContext/seLinuxOptions").size(), 1);
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

        JsonNode secret = NextflowKubernetesEngine.secret(run(), "rcrun_secret", Optional.empty());
        assertEquals(secret.at("/metadata/name").asString(), "nextflow-" + RUN_ID + "-tes-token");
        assertEquals(secret.at("/stringData/token").asString(), "rcrun_secret");
        assertEquals(secret.path("stringData").size(), 1);
        assertEquals(secret.at("/metadata/labels/refcloud.ga4gh.org~1run-id").asString(), RUN_ID);
    }

    // on S3 storage the run's own credentials go in the same Secret, as the AWS SDK's environment variables (spec 0012)
    @Test
    public void storageCredentialsGoInTheRunsSecret() {
        RunCredentials credentials =
                new RunCredentials("ASIAKEY", "secret-key", "session-token", Instant.parse("2026-11-18T12:00:00Z"));

        JsonNode data = NextflowKubernetesEngine.secret(run(), "rcrun_secret", Optional.of(credentials))
                .path("stringData");

        assertEquals(data.path("token").asString(), "rcrun_secret");
        assertEquals(data.path("AWS_ACCESS_KEY_ID").asString(), "ASIAKEY");
        assertEquals(data.path("AWS_SECRET_ACCESS_KEY").asString(), "secret-key");
        assertEquals(data.path("AWS_SESSION_TOKEN").asString(), "session-token");
        // an allowlist entry can't set them, or anything else that would point the AWS SDK elsewhere
        NextflowKubernetesEngine engine = new NextflowKubernetesEngine(null, null, null, null);
        assertTrue(engine.isReservedEnvironmentVariable("AWS_SESSION_TOKEN"));
        assertTrue(engine.isReservedEnvironmentVariable("AWS_ENDPOINT_URL"));
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
        NextflowKubernetesEngine engine = new NextflowKubernetesEngine(null, null, null, null);
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
        ComputeConfig.Engine docker = new ComputeConfig.Engine(
                "nextflow", "26.10.5", "1.5.0", null, null, null, null, null, null, null, null);
        ComputeConfig.Engine noNamespace = new ComputeConfig.Engine(
                null, "26.10.5", "1.5.0", "image:v1", null, null, null, null, null, null, null);

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
        ComputeConfig.Engine defaults = new ComputeConfig.Engine(
                null, "26.10.5", "1.5.0", "image:v1", null, "ns", null, null, null, null, null);

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
