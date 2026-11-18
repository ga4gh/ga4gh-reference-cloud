package org.ga4gh.refcloud.api.compute;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// compute connections (D9): how to reach each engine + executor setup, and the deployer's pooled limits for it.
// they are defined only in the connections file (connectionsFile, imported through spring.config.import), which the
// admin CLI also reads to validate compute platforms, so per-property environment overrides are not supported.
// a malformed connection, or an unknown type, fails startup
@Validated
@ConfigurationProperties(prefix = "ga4gh.refcloud.compute")
public record ComputeConfig(
        @NotBlank String connectionsFile,
        @Valid Map<@Pattern(regexp = CONNECTION_KEY_PATTERN) String, @NotNull @Valid Connection> connections) {

    public static final String CONNECTION_KEY_PATTERN = "^[a-z0-9][a-z0-9-]{0,62}$";

    public ComputeConfig {
        connections = connections == null ? Map.of() : Map.copyOf(connections);
    }

    public record Connection(
            @NotNull ComputePlatformType type,
            @NotNull @Valid Engine engine,
            @NotNull @Valid Executor executor,
            @NotNull @Valid Storage storage,
            @NotNull @Valid Limits limits) {

        // the docker type runs nextflow on the host, so its storage is a host directory (spec 0012, decision 1)
        @AssertTrue(message = "nextflow-funnel-docker supports only shared-filesystem storage")
        public boolean isStorageValidForType() {
            if (type == null || storage == null || storage.type() == null) {
                return true; // reported by @NotNull
            }
            return type != ComputePlatformType.NEXTFLOW_FUNNEL_DOCKER
                    || storage.type() == StorageType.SHARED_FILESYSTEM;
        }

        // each platform type needs its own engine settings (see Engine)
        @AssertTrue(
                message = "engine settings don't match the type: nextflow-funnel-docker needs nextflow-executable;"
                        + " nextflow-funnel-kubernetes needs image and namespace (and no nextflow-executable)")
        public boolean isEngineValidForType() {
            if (type == null || engine == null) {
                return true; // reported by @NotNull
            }
            return switch (type) {
                case NEXTFLOW_FUNNEL_DOCKER -> isSet(engine.nextflowExecutable());
                case NEXTFLOW_FUNNEL_KUBERNETES ->
                    !isSet(engine.nextflowExecutable()) && isSet(engine.image()) && isSet(engine.namespace());
            };
        }

        private static boolean isSet(String value) {
            return value != null && !value.isBlank();
        }
    }

    // nextflow engines. the versions are pinned for every type. nextflow-funnel-docker runs nextflowExecutable on the
    // host; nextflow-funnel-kubernetes runs a Job per run from image, in namespace (where the connection's task Jobs
    // run too), sized by cpuCores and memoryGb (defaults: 1 core, 1 GB)
    public record Engine(
            String nextflowExecutable,
            @NotBlank String nextflowVersion,
            @NotBlank String nfGa4ghVersion,
            String image,
            ImagePullPolicy imagePullPolicy,
            @Pattern(regexp = KUBERNETES_NAME_PATTERN) String namespace,
            @Positive Integer cpuCores,
            @Positive BigDecimal memoryGb) {

        public Engine {
            imagePullPolicy = imagePullPolicy == null ? ImagePullPolicy.IF_NOT_PRESENT : imagePullPolicy;
            cpuCores = cpuCores == null ? Integer.valueOf(1) : cpuCores;
            memoryGb = memoryGb == null ? BigDecimal.ONE : memoryGb;
        }
    }

    // a kubernetes namespace name (DNS label)
    public static final String KUBERNETES_NAME_PATTERN = "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$";

    // kubernetes imagePullPolicy values, written in kebab-case in the connections file (if-not-present, always, never)
    public enum ImagePullPolicy {
        IF_NOT_PRESENT("IfNotPresent"),
        ALWAYS("Always"),
        NEVER("Never");

        private final String kubernetesValue;

        ImagePullPolicy(String kubernetesValue) {
            this.kubernetesValue = kubernetesValue;
        }

        public String kubernetesValue() {
            return kubernetesValue;
        }
    }

    // credentialEnv names the environment variable holding the executor's credential; the credential itself is
    // never in the connections file. null when the executor has no auth (e.g. local funnel)
    public record Executor(@NotNull URI tesUrl, String credentialEnv) {}

    // where runs keep their files (D15, D18). shared-filesystem: root, a directory every component mounts at the same
    // path (relative resolves from the connections file's directory, see ComputeConnections). s3 (spec 0012): a bucket
    // and an optional key prefix (e.g. "refcloud/"); endpoint and pathStyleAccess for S3-compatible servers (none for
    // AWS); credentials from the environment variables named by accessKeyIdEnv and secretAccessKeyEnv, or the AWS SDK's
    // default chain (e.g. EKS Pod Identity) when they're not set.
    // each run's engine gets its own temporary credentials, limited to the run's prefix (Task 3): STS AssumeRole of
    // runRoleArn (on AWS, a role core-api's own may assume; S3-compatible servers such as RustFS issue them for
    // core-api's
    // own user and ignore the ARN, which is still required), lasting up to runCredentialsMaxSeconds (default 3600: on
    // AWS, credentials assumed from core-api's own role session last at most an hour; RustFS allows 43200)
    public record Storage(
            @NotNull StorageType type,
            String root,
            @Pattern(regexp = S3_BUCKET_PATTERN) String bucket,
            @Pattern(regexp = S3_PREFIX_PATTERN) String prefix,
            String region,
            URI endpoint,
            Boolean pathStyleAccess,
            @Pattern(regexp = ENVIRONMENT_VARIABLE_PATTERN) String accessKeyIdEnv,
            @Pattern(regexp = ENVIRONMENT_VARIABLE_PATTERN) String secretAccessKeyEnv,
            @Pattern(regexp = IAM_ROLE_ARN_PATTERN) String runRoleArn,

            @Min(MIN_CREDENTIAL_SECONDS) @Max(MAX_CREDENTIAL_SECONDS)
            Integer runCredentialsMaxSeconds) {

        // STS issues credentials for 15 minutes to 12 hours
        public static final int MIN_CREDENTIAL_SECONDS = 900;

        public static final int MAX_CREDENTIAL_SECONDS = 43200;

        public static final int DEFAULT_RUN_CREDENTIALS_MAX_SECONDS = 3600;

        public Storage {
            prefix = prefix == null ? "" : prefix;
            pathStyleAccess = pathStyleAccess != null && pathStyleAccess;
            if (type == StorageType.S3 && runCredentialsMaxSeconds == null) {
                runCredentialsMaxSeconds = DEFAULT_RUN_CREDENTIALS_MAX_SECONDS;
            }
        }

        // each storage type takes only its own settings
        @AssertTrue(
                message = "storage settings don't match the type: shared-filesystem needs root (and no s3 settings);"
                        + " s3 needs bucket, region and run-role-arn (and no root), and either both or neither of"
                        + " access-key-id-env and secret-access-key-env")
        public boolean isValidForType() {
            if (type == null) {
                return true; // reported by @NotNull
            }
            boolean anyS3 = bucket != null
                    || !prefix.isEmpty()
                    || region != null
                    || endpoint != null
                    || pathStyleAccess
                    || accessKeyIdEnv != null
                    || secretAccessKeyEnv != null
                    || runRoleArn != null
                    || (type != StorageType.S3 && runCredentialsMaxSeconds != null);
            return switch (type) {
                case SHARED_FILESYSTEM -> isSet(root) && !anyS3;
                case S3 ->
                    root == null
                            && isSet(bucket)
                            && isSet(region)
                            && isSet(runRoleArn)
                            && (accessKeyIdEnv == null) == (secretAccessKeyEnv == null);
            };
        }

        private static boolean isSet(String value) {
            return value != null && !value.isBlank();
        }
    }

    // an S3 bucket name (lower case, digits, dots and hyphens; 3-63 characters)
    public static final String S3_BUCKET_PATTERN = "^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$";

    // empty, or path segments ending in "/" (e.g. "refcloud/" or "a/b/"): no leading "/", no "." or ".." segments, and
    // only characters that need no escaping in a URL (the Nextflow Job's own S3 requests put the key in one)
    public static final String S3_PREFIX_PATTERN = "^((?!\\.{1,2}/)[A-Za-z0-9_.-]+/)*$";

    public static final String ENVIRONMENT_VARIABLE_PATTERN = "^[A-Za-z_][A-Za-z0-9_]*$";

    // an IAM role ARN, e.g. arn:aws:iam::123456789012:role/refcloud-compute-run
    public static final String IAM_ROLE_ARN_PATTERN = "^arn:aws[a-z-]*:iam::[0-9]{12}:role/[A-Za-z0-9+=,.@_/-]+$";

    // pooled limits for everyone on this connection (D11). sizes in GB, durations in seconds, budgets in core-hours.
    // monthlyCoreHoursBudget is optional (null = no pooled budget); the rest are required
    public record Limits(
            @Positive int maxConcurrentRuns,
            @Positive int maxTasksInFlight,
            @Positive int maxCpuCoresInFlight,
            @NotNull @Positive BigDecimal maxMemoryGbInFlight,
            @Positive int maxTaskCpuCores,
            @NotNull @Positive BigDecimal maxTaskMemoryGb,
            @NotNull @Positive BigDecimal maxTaskDiskGb,
            @Positive int maxRunDurationSeconds,
            @Positive BigDecimal monthlyCoreHoursBudget,
            // what a task that requests no resources is treated as requesting (limits, accounting), and is sent with
            @Positive int defaultTaskCpuCores,
            @NotNull @Positive BigDecimal defaultTaskMemoryGb,
            // the most a run's staged DRS inputs may add up to (GB = 10^9 bytes), checked before anything is
            // downloaded (Task 8)
            @NotNull @Positive BigDecimal maxRunInputsGb) {

        // a default request has to fit within the per-task ceilings
        @AssertTrue(message = "default task resources must not exceed max-task-cpu-cores / max-task-memory-gb")
        public boolean isDefaultTaskWithinCeilings() {
            return defaultTaskCpuCores <= maxTaskCpuCores
                    && (defaultTaskMemoryGb == null
                            || maxTaskMemoryGb == null
                            || defaultTaskMemoryGb.compareTo(maxTaskMemoryGb) <= 0);
        }
    }

    public enum StorageType {
        // a directory shared by the engine and the executor (a host directory, or a hostPath / shared volume)
        SHARED_FILESYSTEM,
        // an S3 bucket (S3 on AWS, or an S3-compatible server such as RustFS locally): spec 0012
        S3
    }
}
