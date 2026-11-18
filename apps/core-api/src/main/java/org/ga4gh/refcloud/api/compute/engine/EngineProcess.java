package org.ga4gh.refcloud.api.compute.engine;

import java.time.Instant;
import java.util.UUID;

// a launched engine, as the run records it. a host process (nextflow-funnel-docker) has a pid and its start time (with
// the pid, this identifies the process: a pid can be reused by an unrelated process later; startedAt is null where the
// OS doesn't report it). a kubernetes Job (nextflow-funnel-kubernetes) has its uid and creation time: its name derives
// from the run id, and the uid tells the run's Job from any other Job with that name
public record EngineProcess(Long pid, UUID jobUid, Instant startedAt) {

    public static EngineProcess hostProcess(long pid, Instant startedAt) {
        return new EngineProcess(pid, null, startedAt);
    }

    public static EngineProcess kubernetesJob(UUID jobUid, Instant createdAt) {
        return new EngineProcess(null, jobUid, createdAt);
    }

    public String describe() {
        return pid != null ? "pid " + pid : "job uid " + jobUid;
    }
}
