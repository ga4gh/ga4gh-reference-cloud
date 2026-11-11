package org.ga4gh.refcloud.api.compute;

import jakarta.validation.Valid;
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
            @NotNull @Valid Limits limits) {}

    // stage 1 has only nextflow engines
    public record Engine(
            @NotBlank String nextflowExecutable,
            @NotBlank String nextflowVersion,
            @NotBlank String nfGa4ghVersion) {}

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
            @Positive BigDecimal monthlyCoreHoursBudget) {}

    public enum StorageType {
        // a directory shared by the engine and the executor (a host directory, or a hostPath / shared volume)
        SHARED_FILESYSTEM
    }
}
