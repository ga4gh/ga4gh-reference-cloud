package org.ga4gh.refcloud.api.compute.engine;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputePlatformType;
import org.ga4gh.refcloud.api.compute.ComputeRunConfig;
import org.ga4gh.refcloud.api.compute.kubernetes.KubernetesClient;
import org.ga4gh.refcloud.api.compute.kubernetes.KubernetesPermission;
import org.ga4gh.refcloud.api.compute.run.ComputeRun;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

// runs a workflow with Nextflow as a kubernetes Job per run, in the connection's compute namespace, sending every task
// to the refcloud TES as stage 1 does (spec 0011, Tasks 1 and 4). the Job runs the Nextflow image
// (compute/images/nextflow) with the run directory mounted from the node at the same path, and stage 1's launcher, so
// the exit-code file in the run directory still says how the engine ended: the Job's own status can't (the launcher
// exits 0 whatever nextflow did). the run-scoped TES token reaches the Job through a per-run Secret, never the Job's
// spec, which Funnel's service accounts can read
@Slf4j
@Component
public class NextflowKubernetesEngine implements WorkflowEngine {

    // the Nextflow image's wrapper: refuses to run when the image's Nextflow or nf-ga4gh version differs from the
    // connection's pins, then runs nextflow (compute/images/nextflow/refcloud-nextflow)
    static final String NEXTFLOW_COMMAND = "refcloud-nextflow";

    static final String APP_LABEL = "app.kubernetes.io/name";

    static final String APP = "refcloud-nextflow";

    static final String RUN_ID_LABEL = "refcloud.ga4gh.org/run-id";

    static final String PLATFORM_ID_LABEL = "refcloud.ga4gh.org/compute-platform-id";

    static final String TOKEN_KEY = "token";

    // the Nextflow image's user, which owns the compute storage (as Funnel's workers and executors do)
    static final long RUN_AS = 10001;

    // core-api enforces the run's max duration itself (cancel, then kill after the grace period); kubernetes stops the
    // Job only this long after that, in case core-api is down
    static final Duration DEADLINE_MARGIN = Duration.ofMinutes(5);

    // a finished Job is deleted at clean-up; this removes it anyway if core-api never gets to it
    static final Duration FINISHED_JOB_TTL = Duration.ofDays(1);

    // waiting reasons that mean the engine's container won't start without someone fixing something. reported only
    // after UNSTARTABLE_GRACE, since an image pull can fail once and then succeed
    static final Set<String> UNSTARTABLE_REASONS = Set.of(
            "ErrImagePull",
            "ImagePullBackOff",
            "ErrImageNeverPull",
            "InvalidImageName",
            "CreateContainerConfigError",
            "CreateContainerError");

    static final Duration UNSTARTABLE_GRACE = Duration.ofMinutes(2);

    // everything this engine does in the connection's namespace (launch, status, terminate, cleanUp), which the
    // connection's health check asks the API server about. keep in sync with core-api's Role in the helm chart
    // (deploy/helm/charts/app/templates/apps/core-api/rbac.yaml)
    public static final List<KubernetesPermission> REQUIRED_PERMISSIONS = List.of(
            new KubernetesPermission("batch", "jobs", "create"),
            new KubernetesPermission("batch", "jobs", "get"),
            new KubernetesPermission("batch", "jobs", "delete"),
            new KubernetesPermission("", "pods", "list"),
            new KubernetesPermission("", "pods", "delete"),
            new KubernetesPermission("", "secrets", "create"),
            new KubernetesPermission("", "secrets", "delete"));

    // the JVM heap, as a share of the Job's memory limit: the rest is the JVM's own overhead
    static final BigDecimal HEAP_SHARE = new BigDecimal("0.625");

    private final KubernetesClient kubernetes;

    private final ComputeRunConfig runConfig;

    private final ObjectMapper objectMapper;

    public NextflowKubernetesEngine(
            KubernetesClient kubernetes, ComputeRunConfig runConfig, ObjectMapper objectMapper) {
        this.kubernetes = kubernetes;
        this.runConfig = runConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public ComputePlatformType platformType() {
        return ComputePlatformType.NEXTFLOW_FUNNEL_KUBERNETES;
    }

    // everything the Job sets (see environment)
    @Override
    public Set<String> reservedEnvironment() {
        return Set.of(
                "NXF_VER",
                "NXF_PLUGINS_DEFAULT",
                "NXF_HOME",
                "NXF_PLUGINS_DIR",
                "NXF_OFFLINE",
                "NXF_OPTS",
                "NXF_TEMP",
                "HOME",
                "REFCLOUD_");
    }

    // writes the run's files, then creates its token Secret and its Job. the Job starts when kubernetes schedules it
    @Override
    public EngineProcess launch(
            ComputeRun run, ComputeConfig.Connection connection, String tesEndpoint, String tesToken)
            throws IOException {
        Path runDirectory = Path.of(run.getRunDirectory());
        NextflowEngine.writeRunFiles(run, tesEndpoint, objectMapper);
        HostProcessRunner.writeLauncher(runDirectory);

        String namespace = connection.engine().namespace();
        kubernetes.create(secretsPath(namespace), secret(run, tesToken));
        try {
            JsonNode job = kubernetes.create(jobsPath(namespace), job(run, connection, runConfig.cancelGracePeriod()));
            JsonNode metadata = job.path("metadata");
            return EngineProcess.kubernetesJob(
                    UUID.fromString(metadata.path("uid").asString()),
                    parseInstant(metadata.path("creationTimestamp").asString("")));
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(secretsPath(namespace) + "/" + secretName(run), null);
            throw exception;
        }
    }

    // the exit-code file first (the launcher writes it as the engine exits), then the Job and its pod
    @Override
    public EngineStatus status(ComputeRun run, ComputeConfig.Connection connection) {
        Path runDirectory = Path.of(run.getRunDirectory());
        Optional<Integer> exitCode = HostProcessRunner.exitCode(runDirectory);
        if (exitCode.isPresent()) {
            return new EngineStatus.Exited(exitCode.get());
        }
        String namespace = connection.engine().namespace();
        try {
            Optional<JsonNode> job =
                    kubernetes.get(jobsPath(namespace) + "/" + jobName(run)).filter(found -> isRunsJob(found, run));
            JsonNode pods = kubernetes.list(podsPath(namespace), RUN_ID_LABEL + "=" + run.getId());
            EngineStatus status = status(job, pods, Instant.now());
            if (status instanceof EngineStatus.Lost) {
                // the pod may have written the exit code since it was first read
                Optional<Integer> late = HostProcessRunner.exitCode(runDirectory);
                if (late.isPresent()) {
                    return new EngineStatus.Exited(late.get());
                }
            }
            return status;
        } catch (IOException exception) {
            // a passing API problem shouldn't fail the run; one that outlasts a cancel can't keep it going forever
            if (run.getCancelRequestedAt() != null
                    && Instant.now()
                            .isAfter(run.getCancelRequestedAt()
                                    .plus(runConfig.cancelGracePeriod().multipliedBy(2)))) {
                return new EngineStatus.Lost(
                        "the engine's kubernetes Job could not be followed: " + exception.getMessage());
            }
            log.warn("could not check run {}'s kubernetes Job: {}", run.getId(), exception.getMessage());
            return new EngineStatus.Alive();
        }
    }

    // the engine's status from its Job (empty if gone) and pods, when there's no exit code
    static EngineStatus status(Optional<JsonNode> job, JsonNode pods, Instant now) {
        if (job.isEmpty()) {
            // a cancelled run's Job is deleted at once, while its pod shuts down (the engine cancels its tasks)
            return pods.isEmpty()
                    ? new EngineStatus.Lost("the engine's kubernetes Job is gone")
                    : new EngineStatus.Alive();
        }
        for (JsonNode condition : job.get().path("status").path("conditions")) {
            if ("Failed".equals(condition.path("type").asString(""))
                    && "True".equals(condition.path("status").asString(""))) {
                return new EngineStatus.Lost("the engine's kubernetes Job failed: "
                        + condition.path("reason").asString("") + " "
                        + condition.path("message").asString(""));
            }
        }
        for (JsonNode pod : pods) {
            Optional<String> unstartable = unstartable(pod, now);
            if (unstartable.isPresent()) {
                return new EngineStatus.Lost("the engine's pod can't start: " + unstartable.get());
            }
        }
        if (job.get().path("status").path("succeeded").asInt(0) > 0) {
            return new EngineStatus.Lost("the engine's kubernetes Job ended without recording an exit code");
        }
        // running, waiting to be scheduled, or pulling its image
        return new EngineStatus.Alive();
    }

    // a container stuck in a waiting state that needs fixing (e.g. its image can't be pulled), past the grace period
    static Optional<String> unstartable(JsonNode pod, Instant now) {
        Instant created =
                parseInstant(pod.path("metadata").path("creationTimestamp").asString(""));
        if (now.isBefore(created.plus(UNSTARTABLE_GRACE))) {
            return Optional.empty();
        }
        for (JsonNode container : pod.path("status").path("containerStatuses")) {
            JsonNode waiting = container.path("state").path("waiting");
            String reason = waiting.path("reason").asString("");
            if (UNSTARTABLE_REASONS.contains(reason)) {
                return Optional.of(reason + ": " + waiting.path("message").asString(""));
            }
        }
        return Optional.empty();
    }

    // deleting the Job deletes its pod, which gets SIGTERM and the run's cancel grace period to stop (the launcher
    // passes
    // the signal on to nextflow, which cancels its tasks). force deletes the pod at once
    @Override
    public void terminate(ComputeRun run, ComputeConfig.Connection connection, boolean force) {
        String namespace = connection.engine().namespace();
        try {
            if (force) {
                for (JsonNode pod : kubernetes.list(podsPath(namespace), RUN_ID_LABEL + "=" + run.getId())) {
                    kubernetes.delete(
                            podsPath(namespace) + "/"
                                    + pod.path("metadata").path("name").asString(),
                            deleteOptions(0));
                }
            }
            kubernetes.delete(jobsPath(namespace) + "/" + jobName(run), deleteOptions(force ? 0 : null));
        } catch (IOException exception) {
            log.warn("could not stop run {}'s kubernetes Job: {}", run.getId(), exception.getMessage());
        }
    }

    // the Job (and its pod), the token Secret, and the run's intermediate directories, as in stage 1. also for runs
    // that never launched, which have neither
    @Override
    public void cleanUp(ComputeRun run, ComputeConfig.Connection connection) {
        String namespace = connection.engine().namespace();
        deleteQuietly(jobsPath(namespace) + "/" + jobName(run), deleteOptions(null));
        deleteQuietly(secretsPath(namespace) + "/" + secretName(run), null);
        NextflowEngine.removeIntermediateDirectories(Path.of(run.getRunDirectory()));
    }

    @Override
    public Optional<String> refcloudFailure(ComputeRun run) {
        return NextflowEngine.refcloudFailureOf(run);
    }

    // the run's Job
    static ObjectNode job(ComputeRun run, ComputeConfig.Connection connection, Duration cancelGracePeriod) {
        JsonNodeFactory json = JsonNodeFactory.instance;
        ComputeConfig.Engine engine = connection.engine();
        String runDirectory = Path.of(run.getRunDirectory()).toString();

        ObjectNode job = json.objectNode();
        job.put("apiVersion", "batch/v1");
        job.put("kind", "Job");
        ObjectNode metadata = job.putObject("metadata");
        metadata.put("name", jobName(run));
        metadata.set("labels", labels(run));

        ObjectNode spec = job.putObject("spec");
        spec.put("backoffLimit", 0);
        spec.put(
                "activeDeadlineSeconds",
                run.getMaxDurationSeconds() + cancelGracePeriod.toSeconds() + DEADLINE_MARGIN.toSeconds());
        spec.put("ttlSecondsAfterFinished", FINISHED_JOB_TTL.toSeconds());

        ObjectNode template = spec.putObject("template");
        template.putObject("metadata").set("labels", labels(run));
        ObjectNode pod = template.putObject("spec");
        pod.put("restartPolicy", "Never");
        // nextflow talks only to the refcloud TES: it needs no kubernetes access, nor the namespace's service variables
        pod.put("automountServiceAccountToken", false);
        pod.put("enableServiceLinks", false);
        pod.put("terminationGracePeriodSeconds", cancelGracePeriod.toSeconds());
        ObjectNode podSecurity = pod.putObject("securityContext");
        podSecurity.put("runAsNonRoot", true);
        podSecurity.put("runAsUser", RUN_AS);
        podSecurity.put("runAsGroup", RUN_AS);
        podSecurity.putObject("seccompProfile").put("type", "RuntimeDefault");

        ObjectNode container = pod.putArray("containers").addObject();
        container.put("name", "nextflow");
        container.put("image", engine.image());
        container.put("imagePullPolicy", engine.imagePullPolicy().kubernetesValue());
        container.put("workingDir", runDirectory);
        ArrayNode command = container.putArray("command");
        command.add("sh");
        command.add(Path.of(runDirectory).resolve(HostProcessRunner.LAUNCHER).toString());
        command.add(runDirectory);
        NextflowEngine.command(run, NEXTFLOW_COMMAND).forEach(command::add);

        ArrayNode env = container.putArray("env");
        environment(run, engine).forEach((name, value) -> {
            ObjectNode variable = env.addObject();
            variable.put("name", name);
            variable.put("value", value);
        });
        ObjectNode token = env.addObject();
        token.put("name", NextflowEngine.TOKEN_ENVIRONMENT_VARIABLE);
        ObjectNode secretKey = token.putObject("valueFrom").putObject("secretKeyRef");
        secretKey.put("name", secretName(run));
        secretKey.put("key", TOKEN_KEY);

        ObjectNode containerSecurity = container.putObject("securityContext");
        containerSecurity.put("allowPrivilegeEscalation", false);
        containerSecurity.put("readOnlyRootFilesystem", true);
        containerSecurity.putObject("capabilities").putArray("drop").add("ALL");

        // limits equal to requests: what the Job reserves is what it gets
        ObjectNode resources = container.putObject("resources");
        for (String kind : new String[] {"requests", "limits"}) {
            ObjectNode amounts = resources.putObject(kind);
            amounts.put("cpu", String.valueOf(engine.cpuCores()));
            amounts.put("memory", engine.memoryGb().stripTrailingZeros().toPlainString() + "G");
        }

        ArrayNode mounts = container.putArray("volumeMounts");
        ObjectNode runMount = mounts.addObject();
        runMount.put("name", "run");
        runMount.put("mountPath", runDirectory);
        ObjectNode tmpMount = mounts.addObject();
        tmpMount.put("name", "tmp");
        tmpMount.put("mountPath", "/tmp");

        // the run directory only, at the same path as on the node and in core-api, so every path in it is the same
        // everywhere (funnel's workers mount the whole storage root)
        ArrayNode volumes = pod.putArray("volumes");
        ObjectNode runVolume = volumes.addObject();
        runVolume.put("name", "run");
        ObjectNode hostPath = runVolume.putObject("hostPath");
        hostPath.put("path", runDirectory);
        hostPath.put("type", "Directory");
        ObjectNode tmpVolume = volumes.addObject();
        tmpVolume.put("name", "tmp");
        tmpVolume.putObject("emptyDir");
        return job;
    }

    // stage 1's pinned environment, plus what running offline in a read-only container needs. sorted, so the Job is
    // the same for the same run
    static Map<String, String> environment(ComputeRun run, ComputeConfig.Engine engine) {
        Map<String, String> environment = new TreeMap<>(NextflowEngine.pinnedEnvironment(run, engine));
        // nextflow writes to its home at startup (e.g. $NXF_HOME/secrets), so it goes on the writable /tmp; the
        // plugins stay in the image, where nf-ga4gh is pre-installed
        environment.put("NXF_HOME", "/tmp/nextflow-home");
        environment.put("NXF_PLUGINS_DIR", "/opt/nextflow/plugins");
        // the Job has no internet: never try to download nextflow or plugins
        environment.put("NXF_OFFLINE", "true");
        environment.put("NXF_OPTS", "-Xmx" + heapMegabytes(engine.memoryGb()) + "m");
        environment.put("NXF_TEMP", "/tmp");
        environment.put("HOME", "/tmp");
        return environment;
    }

    static long heapMegabytes(BigDecimal memoryGb) {
        return memoryGb.multiply(BigDecimal.valueOf(1000))
                .multiply(HEAP_SHARE)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }

    static ObjectNode secret(ComputeRun run, String tesToken) {
        ObjectNode secret = JsonNodeFactory.instance.objectNode();
        secret.put("apiVersion", "v1");
        secret.put("kind", "Secret");
        ObjectNode metadata = secret.putObject("metadata");
        metadata.put("name", secretName(run));
        metadata.set("labels", labels(run));
        secret.put("type", "Opaque");
        secret.putObject("stringData").put(TOKEN_KEY, tesToken);
        return secret;
    }

    static ObjectNode labels(ComputeRun run) {
        ObjectNode labels = JsonNodeFactory.instance.objectNode();
        labels.put(APP_LABEL, APP);
        labels.put(RUN_ID_LABEL, run.getId());
        labels.put(PLATFORM_ID_LABEL, run.getComputePlatformId());
        return labels;
    }

    // run ids are lowercase UUIDs, so these are valid kubernetes names
    static String jobName(ComputeRun run) {
        return "nextflow-" + run.getId();
    }

    static String secretName(ComputeRun run) {
        return "nextflow-" + run.getId() + "-tes-token";
    }

    // background: the Job goes at once and the garbage collector deletes its pod, with its grace period (or at once)
    private static ObjectNode deleteOptions(Integer gracePeriodSeconds) {
        ObjectNode options = JsonNodeFactory.instance.objectNode();
        options.put("propagationPolicy", "Background");
        if (gracePeriodSeconds != null) {
            options.put("gracePeriodSeconds", gracePeriodSeconds);
        }
        return options;
    }

    // the Job with this name is the one the run launched (not one created later under the same name)
    private static boolean isRunsJob(JsonNode job, ComputeRun run) {
        return run.getEngineJobUid() == null
                || run.getEngineJobUid()
                        .toString()
                        .equals(job.path("metadata").path("uid").asString(""));
    }

    private void deleteQuietly(String path, ObjectNode options) {
        try {
            kubernetes.delete(path, options);
        } catch (IOException exception) {
            log.warn("could not delete {}: {}", path, exception.getMessage());
        }
    }

    private static Instant parseInstant(String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException exception) {
            return Instant.now();
        }
    }

    private static String jobsPath(String namespace) {
        return "/apis/batch/v1/namespaces/" + namespace + "/jobs";
    }

    private static String podsPath(String namespace) {
        return "/api/v1/namespaces/" + namespace + "/pods";
    }

    private static String secretsPath(String namespace) {
        return "/api/v1/namespaces/" + namespace + "/secrets";
    }
}
