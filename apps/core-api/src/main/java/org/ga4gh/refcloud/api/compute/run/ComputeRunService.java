package org.ga4gh.refcloud.api.compute.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputeConnections;
import org.ga4gh.refcloud.api.compute.ComputePlatformType;
import org.ga4gh.refcloud.api.compute.ComputeRunConfig;
import org.ga4gh.refcloud.api.compute.engine.EngineProcess;
import org.ga4gh.refcloud.api.compute.engine.EngineStatus;
import org.ga4gh.refcloud.api.compute.engine.WorkflowEngine;
import org.ga4gh.refcloud.api.compute.platform.ComputePlatform;
import org.ga4gh.refcloud.api.compute.platform.ComputePlatformRepository;
import org.ga4gh.refcloud.api.compute.tes.TesTaskService;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

// the run lifecycle (Task 7). WES (Task 8) prepares a run directory and submits; from there runs are queued until the
// connection's pooled max-concurrent-runs allows, launched with a run-scoped TES token, polled through files in their
// run directory (so core-api reattaches after a restart), cancelled when overdue, and cleaned up when they end.
//
// methods aren't @Transactional: each repository call is its own transaction, so no transaction is held open while an
// engine starts or stops. launching, polling and cancelling are serialized per connection (one core-api instance)
@Slf4j
@Service
public class ComputeRunService {

    private final ComputeRunRepository computeRunRepository;

    private final ComputePlatformRepository computePlatformRepository;

    private final ComputeConnections computeConnections;

    private final ComputeRunConfig runConfig;

    private final TesTaskService tesTaskService;

    private final Map<ComputePlatformType, WorkflowEngine> engines;

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ComputeRunService(
            ComputeRunRepository computeRunRepository,
            ComputePlatformRepository computePlatformRepository,
            ComputeConnections computeConnections,
            ComputeRunConfig runConfig,
            TesTaskService tesTaskService,
            List<WorkflowEngine> engines) {
        this.computeRunRepository = computeRunRepository;
        this.computePlatformRepository = computePlatformRepository;
        this.computeConnections = computeConnections;
        this.runConfig = runConfig;
        this.tesTaskService = tesTaskService;
        this.engines = engines.stream().collect(Collectors.toMap(WorkflowEngine::platformType, Function.identity()));
    }

    // a new run's id and directory (runs/<run_id>/ under the connection's storage root, with inputs/, work/ and
    // outputs/), for WES to put the workflow and staged inputs into before submitting
    public RunDirectory allocateRunDirectory(ComputePlatform platform) throws IOException {
        Path storageRoot = computeConnections
                .storageRoot(platform.getConnectionKey())
                .orElseThrow(() -> new IllegalStateException(
                        "compute platform " + platform.getId() + " has no connection " + platform.getConnectionKey()));
        String runId = UUID.randomUUID().toString();
        Path runDirectory = storageRoot.resolve("runs").resolve(runId);
        for (String child : List.of("inputs", "work", "outputs")) {
            Files.createDirectories(runDirectory.resolve(child));
        }
        return new RunDirectory(runId, runDirectory);
    }

    // records a prepared run as QUEUED and starts it right away if the connection has capacity. returns the run id
    public String submit(ComputePlatform platform, String userId, RunDirectory directory, RunSubmission submission) {
        ComputeConfig.Connection connection = connection(platform);
        WorkflowEngine engine = engine(connection);

        Path workflowPath = directory.path().resolve(submission.workflowPath()).normalize();
        if (!workflowPath.startsWith(directory.path()) || !Files.exists(workflowPath)) {
            throw new BadRequestException(
                    "the workflow must be inside the run directory: " + submission.workflowPath());
        }
        for (String name : submission.engineEnv().keySet()) {
            if (engine.isReservedEnvironmentVariable(name)) {
                throw new BadRequestException("engine environment variable " + name + " is set by the refcloud");
            }
        }
        JsonNode params = submission.workflowParams() == null
                ? JsonNodeFactory.instance.objectNode()
                : submission.workflowParams();
        if (!params.isObject()) {
            throw new BadRequestException("workflow_params must be a JSON object");
        }

        ComputeRun run = ComputeRun.builder()
                .id(directory.runId())
                .computePlatformId(platform.getId())
                .userId(userId)
                .state(ComputeRunStates.QUEUED)
                .runDirectory(directory.path().toString())
                .workflowPath(workflowPath.toString())
                .workflowParams(params)
                .engineProfiles(List.copyOf(submission.engineProfiles()))
                .engineEnv(Map.copyOf(submission.engineEnv()))
                .request(submission.request())
                .maxDurationSeconds(maxDurationSeconds(platform, connection))
                .createdAt(Instant.now())
                .build();
        computeRunRepository.save(run);

        withLock(platform.getConnectionKey(), () -> launchWaiting(platform.getConnectionKey(), connection));
        return run.getId();
    }

    public Optional<ComputeRun> findRun(String runId) {
        return computeRunRepository.findById(runId);
    }

    // a queued run is cancelled at once; a running one gets SIGTERM (its engine cancels its own tasks) and is killed if
    // it hasn't exited after the grace period. cancelling a finished run does nothing
    public void cancel(String runId, String reason) {
        ComputeRun found = computeRunRepository
                .findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("no run " + runId));
        ComputePlatform platform =
                computePlatformRepository.findById(found.getComputePlatformId()).orElseThrow();
        withLock(platform.getConnectionKey(), () -> {
            ComputeRun run = computeRunRepository.findById(runId).orElseThrow();
            if (ComputeRunStates.isTerminal(run.getState()) || run.getCancelRequestedAt() != null) {
                return;
            }
            if (ComputeRunStates.QUEUED.equals(run.getState())) {
                run.setStateReason(reason);
                finish(run, engine(connection(platform)), ComputeRunStates.CANCELED, null);
                return;
            }
            requestCancel(run, connection(platform), reason);
        });
    }

    // checks every active run (finished? lost? overdue? past its cancel grace period?) and then starts queued runs
    // while
    // there's capacity, for every configured connection. also how core-api reattaches after a restart: the first
    // round finds runs whose engines are still alive and carries on following them
    @Scheduled(initialDelay = 0, fixedDelayString = "${ga4gh.refcloud.compute.runs.poll-interval}")
    public void pollRuns() {
        for (String connectionKey : computeConnections.keys()) {
            ComputeConfig.Connection connection =
                    computeConnections.find(connectionKey).orElseThrow();
            withLock(connectionKey, () -> {
                for (ComputeRun run :
                        computeRunRepository.findByConnectionAndStates(connectionKey, ComputeRunStates.ACTIVE)) {
                    try {
                        poll(run, connection);
                    } catch (RuntimeException exception) {
                        log.error("could not check run {}", run.getId(), exception);
                    }
                }
                launchWaiting(connectionKey, connection);
            });
        }
    }

    private void poll(ComputeRun run, ComputeConfig.Connection connection) {
        WorkflowEngine engine = engine(connection);
        if (run.getEnginePid() == null) {
            // core-api stopped partway through a launch
            Instant since = run.getStartedAt() != null ? run.getStartedAt() : run.getCreatedAt();
            if (Instant.now().isAfter(since.plus(runConfig.launchTimeout().multipliedBy(2)))) {
                run.setStateReason("the engine did not finish launching");
                finish(run, engine, ComputeRunStates.SYSTEM_ERROR, null);
            }
            return;
        }

        EngineStatus status = engine.status(run);
        switch (status) {
            case EngineStatus.Exited exited -> {
                // only a failure needs its cause looked at
                Optional<String> refcloudFailure =
                        exited.exitCode() == 0 ? Optional.empty() : engine.refcloudFailure(run);
                finish(run, engine, outcome(run, exited.exitCode(), refcloudFailure), exited.exitCode());
            }
            case EngineStatus.Lost lost -> {
                if (run.getCancelRequestedAt() == null) {
                    run.setStateReason("the engine process was lost (it was killed, or stopped along with core-api)");
                }
                finish(
                        run,
                        engine,
                        run.getCancelRequestedAt() != null ? ComputeRunStates.CANCELED : ComputeRunStates.SYSTEM_ERROR,
                        null);
            }
            case EngineStatus.Alive alive -> {
                Instant now = Instant.now();
                if (run.getCancelRequestedAt() != null) {
                    if (now.isAfter(run.getCancelRequestedAt().plus(runConfig.cancelGracePeriod()))) {
                        log.warn(
                                "run {} did not stop within {} of a cancel; killing it",
                                run.getId(),
                                runConfig.cancelGracePeriod());
                        engine.terminate(run, true);
                    }
                } else if (run.getStartedAt() != null
                        && now.isAfter(run.getStartedAt().plusSeconds(run.getMaxDurationSeconds()))) {
                    requestCancel(
                            run,
                            connection,
                            "exceeded the max run duration of " + Duration.ofSeconds(run.getMaxDurationSeconds()));
                }
            }
        }
    }

    // a cancelled run is CANCELED however its engine exited (nextflow exits non-zero on SIGTERM). 126 and 127 are the
    // shell's "not executable" and "command not found": the engine never ran, which is a system problem, not a failed
    // workflow
    // a failure caused by the engine losing contact with the refcloud (refcloudFailure, e.g. core-api down for longer
    // than nf-ga4gh tolerates) is also a system problem, not a failed workflow
    static String outcome(ComputeRun run, int exitCode, Optional<String> refcloudFailure) {
        if (run.getCancelRequestedAt() != null) {
            return ComputeRunStates.CANCELED;
        }
        if (exitCode == 0) {
            return ComputeRunStates.COMPLETE;
        }
        if (exitCode == 126 || exitCode == 127) {
            run.setStateReason("the engine could not be started (exit code " + exitCode + ")");
            return ComputeRunStates.SYSTEM_ERROR;
        }
        if (refcloudFailure.isPresent()) {
            run.setStateReason(refcloudFailure.get());
            return ComputeRunStates.SYSTEM_ERROR;
        }
        return ComputeRunStates.EXECUTOR_ERROR;
    }

    // starts queued runs, oldest first, while the connection's running count is below max-concurrent-runs
    private void launchWaiting(String connectionKey, ComputeConfig.Connection connection) {
        int active = computeRunRepository
                .findByConnectionAndStates(connectionKey, ComputeRunStates.ACTIVE)
                .size();
        for (ComputeRun run :
                computeRunRepository.findByConnectionAndStates(connectionKey, List.of(ComputeRunStates.QUEUED))) {
            if (active >= connection.limits().maxConcurrentRuns()) {
                return;
            }
            launch(run, connection);
            active++;
        }
    }

    private void launch(ComputeRun run, ComputeConfig.Connection connection) {
        WorkflowEngine engine = engine(connection);
        String token = RunTokens.mint();
        run.setState(ComputeRunStates.INITIALIZING);
        run.setTokenHash(RunTokens.hash(token));
        computeRunRepository.save(run);

        String tesEndpoint = runConfig.engineCallbackBase() + "/ga4gh/tes/" + run.getComputePlatformId();
        try {
            EngineProcess process = engine.launch(run, connection, tesEndpoint, token);
            run.setEnginePid(process.pid());
            run.setEngineStartedAt(process.startedAt());
            run.setState(ComputeRunStates.RUNNING);
            run.setStartedAt(Instant.now());
            computeRunRepository.save(run);
            log.info("run {} started (pid {})", run.getId(), process.pid());
        } catch (IOException | RuntimeException exception) {
            log.error("could not launch run {}", run.getId(), exception);
            run.setStateReason("the engine could not be launched: " + exception.getMessage());
            finish(run, engine, ComputeRunStates.SYSTEM_ERROR, null);
        }
    }

    private void requestCancel(ComputeRun run, ComputeConfig.Connection connection, String reason) {
        run.setCancelRequestedAt(Instant.now());
        run.setStateReason(reason);
        run.setState(ComputeRunStates.CANCELING);
        computeRunRepository.save(run);
        engine(connection).terminate(run, false);
        log.info("run {} canceling: {}", run.getId(), reason);
    }

    // the run's final state: its token stops working, any task it left behind is cancelled, and the run directory keeps
    // only outputs and logs
    private void finish(ComputeRun run, WorkflowEngine engine, String state, Integer exitCode) {
        run.setState(state);
        run.setExitCode(exitCode);
        run.setTokenHash(null);
        run.setEndedAt(Instant.now());
        computeRunRepository.save(run);
        log.info(
                "run {} ended {}{}",
                run.getId(),
                state,
                run.getStateReason() == null ? "" : ": " + run.getStateReason());

        tesTaskService.cancelRunTasks(run.getId());
        engine.cleanUp(run);
    }

    private int maxDurationSeconds(ComputePlatform platform, ComputeConfig.Connection connection) {
        int ceiling = connection.limits().maxRunDurationSeconds();
        Integer perUser = platform.getUserMaxRunDurationSeconds();
        return perUser == null ? ceiling : Math.min(perUser, ceiling);
    }

    private ComputeConfig.Connection connection(ComputePlatform platform) {
        return computeConnections
                .find(platform.getConnectionKey())
                .orElseThrow(() -> new IllegalStateException(
                        "compute platform " + platform.getId() + " has no connection " + platform.getConnectionKey()));
    }

    private WorkflowEngine engine(ComputeConfig.Connection connection) {
        WorkflowEngine engine = engines.get(connection.type());
        if (engine == null) {
            throw new IllegalStateException(
                    "no workflow engine for platform type " + connection.type().key());
        }
        return engine;
    }

    private void withLock(String connectionKey, Runnable action) {
        ReentrantLock lock = locks.computeIfAbsent(connectionKey, key -> new ReentrantLock());
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    // a run's id and directory, allocated before the run is submitted
    public record RunDirectory(String runId, Path path) {}

    // what WES hands over (Task 8): the workflow (relative to the run directory, already in place), the researcher's
    // workflow_params (with inputs already staged), the allowlist entry's engine settings, and the WES request
    public record RunSubmission(
            String workflowPath,
            JsonNode workflowParams,
            List<String> engineProfiles,
            Map<String, String> engineEnv,
            JsonNode request) {}
}
