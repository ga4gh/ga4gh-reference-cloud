package org.ga4gh.refcloud.api.compute.run;

import java.util.Set;

// WES 1.1.0 run states, as stored in compute_run.state
public final class ComputeRunStates {

    public static final String QUEUED = "QUEUED";
    public static final String INITIALIZING = "INITIALIZING";
    public static final String RUNNING = "RUNNING";
    public static final String CANCELING = "CANCELING";
    public static final String COMPLETE = "COMPLETE";
    public static final String EXECUTOR_ERROR = "EXECUTOR_ERROR";
    public static final String SYSTEM_ERROR = "SYSTEM_ERROR";
    public static final String CANCELED = "CANCELED";

    // launched and not finished: these use pooled capacity, are polled, and their run token is valid (the engine
    // still needs TES while canceling, to cancel its own tasks)
    public static final Set<String> ACTIVE = Set.of(INITIALIZING, RUNNING, "PAUSED", CANCELING, "UNKNOWN");

    public static final Set<String> TERMINAL = Set.of(COMPLETE, EXECUTOR_ERROR, SYSTEM_ERROR, CANCELED, "PREEMPTED");

    private ComputeRunStates() {}

    public static boolean isTerminal(String state) {
        return TERMINAL.contains(state);
    }
}
