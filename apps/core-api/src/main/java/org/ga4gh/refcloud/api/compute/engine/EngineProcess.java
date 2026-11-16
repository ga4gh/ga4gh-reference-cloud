package org.ga4gh.refcloud.api.compute.engine;

import java.time.Instant;

// a launched engine: its pid, and its start time (with the pid, this identifies the process: a pid can be reused by an
// unrelated process later). startedAt is null where the OS doesn't report it
public record EngineProcess(long pid, Instant startedAt) {}
