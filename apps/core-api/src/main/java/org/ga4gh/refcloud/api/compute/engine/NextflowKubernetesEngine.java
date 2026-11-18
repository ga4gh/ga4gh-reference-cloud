package org.ga4gh.refcloud.api.compute.engine;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
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
import org.ga4gh.refcloud.api.compute.storage.RunCredentials;
import org.ga4gh.refcloud.api.compute.storage.RunStorage;
import org.ga4gh.refcloud.api.compute.storage.RunStorages;
import org.ga4gh.refcloud.api.compute.storage.S3Location;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

// runs a workflow with Nextflow as a kubernetes Job per run, in the connection's compute namespace, sending every task
// to the refcloud TES as stage 1 does (spec 0011, Tasks 1 and 4). the Job runs the Nextflow image
// (compute/images/nextflow), and the exit-code file its launcher writes says how the engine ended: the Job's own status
// can't (the launcher exits 0 whatever nextflow did). the run's files are its storage's (RunStorage):
//   * on a shared filesystem, the run directory is mounted from the node at the same path, with stage 1's launcher;
//   * on S3 (spec 0012), the Job keeps the engine's files in its own emptyDir: the image's refcloud-run-s3 fetches the
//     workflow, config and params from the run's prefix, uploads the logs while nextflow runs, and the logs and exit
//     code when it ends. nextflow's work directory and outputs are the run's s3:// prefixes
// the run-scoped TES token (and on S3, the run's own storage credentials) reach the Job through a per-run Secret,
// never the Job's spec, which Funnel's service accounts can read
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

    // on S3 storage: the image's launcher, the Job's own directory for the engine's files, where the run's prefix is
    // (its URL), and how often the logs are uploaded while the engine runs (compute/images/nextflow/refcloud-run-s3)
    static final String S3_LAUNCHER = "refcloud-run-s3";

    static final String S3_RUN_DIRECTORY = "/refcloud/run";

    static final String RUN_URL_VARIABLE = "REFCLOUD_RUN_URL";

    static final String LOG_SYNC_VARIABLE = "REFCLOUD_LOG_SYNC_SECONDS";

    static final int LOG_SYNC_SECONDS = 15;

    // the Nextflow image's user, which owns the compute storage (as Funnel's workers and executors do)
    static final long RUN_AS = 10001;

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

    private final RunStorages runStorages;

    public NextflowKubernetesEngine(
            KubernetesClient kubernetes,
            ComputeRunConfig runConfig,
            ObjectMapper objectMapper,
            RunStorages runStorages) {
        this.kubernetes = kubernetes;
        this.runConfig = runConfig;
        this.objectMapper = objectMapper;
        this.runStorages = runStorages;
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
                "REFCLOUD_",
                // the run's storage credentials, and anything else that would point the AWS SDK elsewhere (spec 0012)
                "AWS_");
    }

    // writes the run's config and params to its storage, then creates its Secret and its Job. the Job starts when
    // kubernetes schedules it
    @Override
    public EngineProcess launch(
            ComputeRun run,
            ComputeConfig.Connection connection,
            String tesEndpoint,
            String tesToken,
            Optional<RunCredentials> storageCredentials)
            throws IOException {
        RunStorage storage = runStorages.forRun(run);
        String location = run.getRunDirectory();
        Optional<S3Access> s3 = s3Access(run, connection.storage(), InetAddress::getByName);
        String config = NextflowEngine.config(withResolvedHost(tesEndpoint, InetAddress::getByName))
                + s3.map(S3Access::nextflowConfig).orElse("");
        storage.writeEngineFile(location, NextflowEngine.CONFIG_FILE, config);
        storage.writeEngineFile(
                location, NextflowEngine.PARAMS_FILE, objectMapper.writeValueAsString(run.getWorkflowParams()));
        if (s3.isEmpty()) {
            HostProcessRunner.writeLauncher(Path.of(location));
        }

        String namespace = connection.engine().namespace();
        kubernetes.create(secretsPath(namespace), secret(run, tesToken, storageCredentials));
        try {
            JsonNode job =
                    kubernetes.create(jobsPath(namespace), job(run, connection, runConfig.cancelGracePeriod(), s3));
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
        String namespace = connection.engine().namespace();
        try {
            RunStorage storage = runStorages.forRun(run);
            Optional<Integer> exitCode = exitCode(storage, run);
            if (exitCode.isPresent()) {
                return new EngineStatus.Exited(exitCode.get());
            }
            Optional<JsonNode> job =
                    kubernetes.get(jobsPath(namespace) + "/" + jobName(run)).filter(found -> isRunsJob(found, run));
            JsonNode pods = kubernetes.list(podsPath(namespace), RUN_ID_LABEL + "=" + run.getId());
            EngineStatus status = status(job, pods, Instant.now());
            if (status instanceof EngineStatus.Lost) {
                // the pod may have written the exit code since it was first read
                Optional<Integer> late = exitCode(storage, run);
                if (late.isPresent()) {
                    return new EngineStatus.Exited(late.get());
                }
            }
            return status;
        } catch (IOException | RuntimeException exception) {
            // a passing problem reaching the kubernetes API or the run's storage shouldn't fail the run; one that
            // outlasts a cancel can't keep it going forever
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

    // the exit code the launcher recorded in the run's storage, if it has
    static Optional<Integer> exitCode(RunStorage storage, ComputeRun run) throws IOException {
        return storage.readEngineFile(run.getRunDirectory(), HostProcessRunner.EXIT_CODE_FILE)
                .flatMap(text -> {
                    try {
                        return Optional.of(Integer.parseInt(text.strip()));
                    } catch (NumberFormatException exception) {
                        return Optional.empty();
                    }
                });
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
    }

    // read from the end of nextflow.log in the run's storage
    @Override
    public Optional<String> refcloudFailure(ComputeRun run) {
        try {
            return NextflowEngine.refcloudFailureIn(
                    run,
                    runStorages
                            .forRun(run)
                            .readEngineFileTail(
                                    run.getRunDirectory(), NextflowEngine.LOG_FILE, NextflowEngine.LOG_TAIL_BYTES));
        } catch (IOException | RuntimeException exception) {
            log.warn("could not read run {}'s nextflow log: {}", run.getId(), exception.getMessage());
            return Optional.empty();
        }
    }

    // how a run on S3 storage reaches its files (spec 0012). an endpoint (an S3-compatible server such as RustFS) is
    // reached by its address, since workflow pods have no DNS, which means path-style URLs; on AWS the bucket's own
    // virtual-hosted URL, which needs DNS (the AWS platform spec)
    record S3Access(String runUrl, String region, String endpoint, NextflowEngine.RunFiles files) {

        // nextflow's S3 client settings (nf-amazon), appended to the run's refcloud.config. the credentials come from
        // the environment
        String nextflowConfig() {
            StringBuilder config = new StringBuilder();
            config.append("aws.region = '").append(groovyString(region)).append("'\n");
            if (endpoint != null) {
                config.append("aws.client.endpoint = '")
                        .append(groovyString(endpoint))
                        .append("'\n");
                config.append("aws.client.s3PathStyleAccess = true\n");
            }
            return config.toString();
        }
    }

    // empty for a run on a shared filesystem
    static Optional<S3Access> s3Access(ComputeRun run, ComputeConfig.Storage storage, Resolver resolver)
            throws IOException {
        String location = run.getRunDirectory();
        if (!S3Location.isS3(location)) {
            return Optional.empty();
        }
        S3Location prefix =
                S3Location.parse(location).orElseThrow(() -> new IOException("invalid run location " + location));
        String endpoint = null;
        String runUrl;
        if (storage.endpoint() != null) {
            endpoint = stripTrailingSlash(withResolvedHost(storage.endpoint().toString(), resolver));
            runUrl = endpoint + "/" + prefix.bucket() + "/" + prefix.key();
        } else {
            runUrl = "https://" + prefix.bucket() + ".s3." + storage.region() + ".amazonaws.com/" + prefix.key();
        }
        if (!run.getWorkflowPath().startsWith(location + "/")) {
            throw new IOException("the run's workflow " + run.getWorkflowPath() + " is outside its location");
        }
        NextflowEngine.RunFiles files = new NextflowEngine.RunFiles(
                S3_RUN_DIRECTORY,
                S3_RUN_DIRECTORY + "/" + run.getWorkflowPath().substring(location.length() + 1),
                location + "/work",
                location + "/outputs");
        return Optional.of(new S3Access(runUrl, storage.region(), endpoint, files));
    }

    // a groovy single-quoted string's content: backslashes, then quotes, escaped
    private static String groovyString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // looks up a host name's address (InetAddress::getByName; a stand-in in tests)
    interface Resolver {
        InetAddress resolve(String host) throws UnknownHostException;
    }

    // the TES endpoint with core-api's host name replaced by its address (its Service's, through core-api's own DNS),
    // so the Nextflow Job needs no DNS: the compute namespace gives workflow pods none (spec 0011, Task 6), since DNS
    // lookups can carry data out of the cluster
    static String withResolvedHost(String url, Resolver resolver) throws IOException {
        URI uri = URI.create(url);
        String address = resolver.resolve(uri.getHost()).getHostAddress();
        try {
            return new URI(
                            uri.getScheme(),
                            uri.getUserInfo(),
                            address,
                            uri.getPort(),
                            uri.getPath(),
                            uri.getQuery(),
                            uri.getFragment())
                    .toString();
        } catch (URISyntaxException exception) {
            throw new IOException("could not build the TES endpoint for " + url, exception);
        }
    }

    // the run's Job on a shared filesystem
    static ObjectNode job(ComputeRun run, ComputeConfig.Connection connection, Duration cancelGracePeriod) {
        return job(run, connection, cancelGracePeriod, Optional.empty());
    }

    // the run's Job: on a shared filesystem (s3 empty), or on S3
    static ObjectNode job(
            ComputeRun run, ComputeConfig.Connection connection, Duration cancelGracePeriod, Optional<S3Access> s3) {
        JsonNodeFactory json = JsonNodeFactory.instance;
        ComputeConfig.Engine engine = connection.engine();
        String runDirectory = s3.isPresent()
                ? S3_RUN_DIRECTORY
                : Path.of(run.getRunDirectory()).toString();

        ObjectNode job = json.objectNode();
        job.put("apiVersion", "batch/v1");
        job.put("kind", "Job");
        ObjectNode metadata = job.putObject("metadata");
        metadata.put("name", jobName(run));
        metadata.set("labels", labels(run));

        ObjectNode spec = job.putObject("spec");
        spec.put("backoffLimit", 0);
        // core-api enforces the run's max duration itself (cancel, then kill after the grace period); kubernetes stops
        // the Job only after the margin too, in case core-api is down (ComputeRunConfig.engineLifetime)
        spec.put(
                "activeDeadlineSeconds",
                run.getMaxDurationSeconds()
                        + cancelGracePeriod.toSeconds()
                        + ComputeRunConfig.ENGINE_DEADLINE_MARGIN.toSeconds());
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
        if (s3.isPresent()) {
            command.add(S3_LAUNCHER);
            command.add(runDirectory);
            NextflowEngine.command(run, s3.get().files(), NEXTFLOW_COMMAND).forEach(command::add);
        } else {
            command.add("sh");
            command.add(
                    Path.of(runDirectory).resolve(HostProcessRunner.LAUNCHER).toString());
            command.add(runDirectory);
            NextflowEngine.command(run, NEXTFLOW_COMMAND).forEach(command::add);
        }

        ArrayNode env = container.putArray("env");
        environment(run, engine, s3).forEach((name, value) -> {
            ObjectNode variable = env.addObject();
            variable.put("name", name);
            variable.put("value", value);
        });
        fromSecret(env, NextflowEngine.TOKEN_ENVIRONMENT_VARIABLE, secretName(run), TOKEN_KEY);
        if (s3.isPresent()) {
            for (String name : List.of(
                    RunCredentials.ACCESS_KEY_ID_VARIABLE,
                    RunCredentials.SECRET_ACCESS_KEY_VARIABLE,
                    RunCredentials.SESSION_TOKEN_VARIABLE)) {
                fromSecret(env, name, secretName(run), name);
            }
        }

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

        // on a shared filesystem, the run directory only, at the same path as on the node and in core-api, so every
        // path in it is the same everywhere (funnel's workers mount the whole storage root). on S3, the Job's own
        ArrayNode volumes = pod.putArray("volumes");
        ObjectNode runVolume = volumes.addObject();
        runVolume.put("name", "run");
        if (s3.isPresent()) {
            runVolume.putObject("emptyDir");
        } else {
            ObjectNode hostPath = runVolume.putObject("hostPath");
            hostPath.put("path", runDirectory);
            hostPath.put("type", "Directory");
        }
        ObjectNode tmpVolume = volumes.addObject();
        tmpVolume.put("name", "tmp");
        tmpVolume.putObject("emptyDir");
        return job;
    }

    // a variable read from the run's Secret, so its value is never in the Job's spec
    private static void fromSecret(ArrayNode env, String name, String secret, String key) {
        ObjectNode variable = env.addObject();
        variable.put("name", name);
        ObjectNode secretKey = variable.putObject("valueFrom").putObject("secretKeyRef");
        secretKey.put("name", secret);
        secretKey.put("key", key);
    }

    // with, on S3, where the run's prefix is and its region (the credentials come from the Secret). the AWS SDK never
    // asks the instance metadata service for credentials: the run has its own
    static Map<String, String> environment(ComputeRun run, ComputeConfig.Engine engine, Optional<S3Access> s3) {
        Map<String, String> environment = environment(run, engine);
        s3.ifPresent(access -> {
            environment.put(RUN_URL_VARIABLE, access.runUrl());
            environment.put(LOG_SYNC_VARIABLE, String.valueOf(LOG_SYNC_SECONDS));
            environment.put("AWS_REGION", access.region());
            environment.put("AWS_EC2_METADATA_DISABLED", "true");
        });
        return environment;
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

    // the run's secrets: its TES token, and on S3 storage its own credentials (spec 0012), under the names the AWS SDK
    // reads from the environment
    static ObjectNode secret(ComputeRun run, String tesToken, Optional<RunCredentials> storageCredentials) {
        ObjectNode secret = JsonNodeFactory.instance.objectNode();
        secret.put("apiVersion", "v1");
        secret.put("kind", "Secret");
        ObjectNode metadata = secret.putObject("metadata");
        metadata.put("name", secretName(run));
        metadata.set("labels", labels(run));
        secret.put("type", "Opaque");
        ObjectNode data = secret.putObject("stringData");
        data.put(TOKEN_KEY, tesToken);
        storageCredentials.ifPresent(credentials -> {
            data.put(RunCredentials.ACCESS_KEY_ID_VARIABLE, credentials.accessKeyId());
            data.put(RunCredentials.SECRET_ACCESS_KEY_VARIABLE, credentials.secretAccessKey());
            data.put(RunCredentials.SESSION_TOKEN_VARIABLE, credentials.sessionToken());
        });
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
