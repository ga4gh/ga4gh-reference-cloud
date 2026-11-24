package org.ga4gh.refcloud.api.compute;

import java.nio.file.Path;
import java.util.Optional;

// who is calling a compute API. a researcher (hydra-issued passport jwt) acts for themselves; a workflow run (its
// run-scoped token, Task 7) acts for the user who submitted it, and may only touch files in its own run directory
public record ComputeCaller(String userId, String runId, Path runDirectory) {

    public static ComputeCaller researcher(String userId) {
        return new ComputeCaller(userId, null, null);
    }

    public static ComputeCaller run(String userId, String runId, Path runDirectory) {
        return new ComputeCaller(userId, runId, runDirectory);
    }

    public boolean isRun() {
        return runId != null;
    }

    public Optional<Path> optionalRunDirectory() {
        return Optional.ofNullable(runDirectory);
    }
}
