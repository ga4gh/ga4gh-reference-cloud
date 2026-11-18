package org.ga4gh.refcloud.api.compute;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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

    // root may be relative: it resolves from the connections file's directory (see ComputeConnections)
    public record Storage(
            @NotNull StorageType type, @NotBlank String root) {}

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
        SHARED_FILESYSTEM
    }
}
