package org.ga4gh.refcloud.api.compute.task;

import java.util.Set;

// TES 1.1 task states, as stored in compute_task.state
public final class ComputeTaskStates {

    public static final String QUEUED = "QUEUED";

    public static final String CANCELING = "CANCELING";

    public static final String CANCELED = "CANCELED";

    public static final String SYSTEM_ERROR = "SYSTEM_ERROR";

    // a task in one of these states is finished: it no longer uses capacity and its usage is final
    public static final Set<String> TERMINAL =
            Set.of("COMPLETE", "EXECUTOR_ERROR", SYSTEM_ERROR, CANCELED, "PREEMPTED");

    public static final Set<String> ALL = Set.of(
            "UNKNOWN",
            QUEUED,
            "INITIALIZING",
            "RUNNING",
            "PAUSED",
            "COMPLETE",
            "EXECUTOR_ERROR",
            SYSTEM_ERROR,
            CANCELED,
            "PREEMPTED",
            CANCELING);

    private ComputeTaskStates() {}

    public static boolean isTerminal(String state) {
        return TERMINAL.contains(state);
    }
}
