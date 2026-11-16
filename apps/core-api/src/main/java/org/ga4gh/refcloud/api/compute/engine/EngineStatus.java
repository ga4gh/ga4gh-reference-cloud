package org.ga4gh.refcloud.api.compute.engine;

// what a run's engine process is doing
public sealed interface EngineStatus {

    // still running
    record Alive() implements EngineStatus {}

    // finished, with its exit code
    record Exited(int exitCode) implements EngineStatus {}

    // gone without an exit code (killed with its launcher, e.g. Ctrl-C in the terminal that started core-api)
    record Lost() implements EngineStatus {}
}
