package org.ga4gh.refcloud.api.compute;

// who is calling a compute API. a researcher (hydra-issued passport jwt) acts for themselves; a workflow run (its
// run-scoped token, Task 7) acts for the user who submitted it, and may only touch files in its own run's location: a
// directory on a shared filesystem, or an s3://<bucket>/<key> prefix (spec 0012)
public record ComputeCaller(String userId, String runId, String runDirectory) {

    public static ComputeCaller researcher(String userId) {
        return new ComputeCaller(userId, null, null);
    }

    public static ComputeCaller run(String userId, String runId, String runDirectory) {
        return new ComputeCaller(userId, runId, runDirectory);
    }

    public boolean isRun() {
        return runId != null;
    }
}
