package org.ga4gh.refcloud.api.compute.engine;

// what a run's engine is doing
public sealed interface EngineStatus {

    // still running (or, on kubernetes, waiting to be scheduled or shutting down)
    record Alive() implements EngineStatus {}

    // finished, with its exit code
    record Exited(int exitCode) implements EngineStatus {}

    // gone without an exit code: killed with its launcher (e.g. Ctrl-C in the terminal that started core-api), or a
    // kubernetes Job that was deleted, hit its deadline or couldn't start. reason says why when it's known (null if
    // not)
    record Lost(String reason) implements EngineStatus {}
}
