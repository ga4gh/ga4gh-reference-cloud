package org.ga4gh.refcloud.api.compute.engine;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputePlatformType;
import org.ga4gh.refcloud.api.compute.run.ComputeRun;

// a workflow engine behind a compute platform type (D12). implementations launch one run at a time as something
// core-api can find again after a restart, and clean up after it. Nextflow is the first; Cromwell would be another
public interface WorkflowEngine {

    ComputePlatformType platformType();

    // environment variable names (or prefixes ending in '_') an allowlist entry's engine settings may not set,
    // because the refcloud sets them for every run
    Set<String> reservedEnvironment();

    // starts the run's engine. tesEndpoint is the refcloud TES the engine sends its tasks to, and tesToken the
    // run-scoped
    // token it authenticates with (only ever passed to the engine process, never stored)
    EngineProcess launch(ComputeRun run, ComputeConfig.Connection connection, String tesEndpoint, String tesToken)
            throws IOException;

    EngineStatus status(ComputeRun run);

    // SIGTERM, or SIGKILL if force
    void terminate(ComputeRun run, boolean force);

    // removes what the run no longer needs once it has ended, keeping its outputs and logs
    void cleanUp(ComputeRun run);

    // after the engine exited with a failure: the reason, if the cause was losing contact with the refcloud's own TES
    // (e.g. core-api was down) rather than the workflow failing. such runs end SYSTEM_ERROR, not EXECUTOR_ERROR
    default Optional<String> refcloudFailure(ComputeRun run) {
        return Optional.empty();
    }

    default boolean isReservedEnvironmentVariable(String name) {
        return reservedEnvironment().stream()
                .anyMatch(reserved -> reserved.endsWith("_") ? name.startsWith(reserved) : name.equals(reserved));
    }
}
