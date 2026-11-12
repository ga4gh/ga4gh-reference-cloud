package org.ga4gh.refcloud.api.compute.tes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.ga4gh.refcloud.api.compute.ComputeCaller;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputeConnections;
import org.ga4gh.refcloud.api.compute.ComputeTaskConfig;
import org.ga4gh.refcloud.api.compute.platform.ComputePlatform;
import org.ga4gh.refcloud.api.compute.task.ComputeTask;
import org.ga4gh.refcloud.api.compute.task.ComputeTaskRepository;
import org.ga4gh.refcloud.api.compute.task.ComputeTaskStates;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.ga4gh.refcloud.api.exception.ForbiddenException;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

// the refcloud's TES tasks (Task 6): accepted and recorded in compute_task, forwarded to the connection's executor
// when pooled capacity allows, kept current by a reconciler, and served to callers from the stored records.
// researchers have read-only TES; only workflow runs (run-scoped tokens, Task 7) create tasks.
//
// methods aren't @Transactional: each repository call is its own transaction, so no transaction is held open
// across a call to the executor. forwarding is serialized per connection (one core-api instance, stage 1)
@Slf4j
@Service
public class TesTaskService {

    private final ComputeTaskRepository computeTaskRepository;

    private final ComputeConnections computeConnections;

    private final TesExecutorClient executorClient;

    private final ComputeTaskConfig taskConfig;

    private final Map<String, ReentrantLock> forwardLocks = new ConcurrentHashMap<>();

    public TesTaskService(
            ComputeTaskRepository computeTaskRepository,
            ComputeConnections computeConnections,
            TesExecutorClient executorClient,
            ComputeTaskConfig taskConfig) {
        this.computeTaskRepository = computeTaskRepository;
        this.computeConnections = computeConnections;
        this.executorClient = executorClient;
        this.taskConfig = taskConfig;
    }

    // POST /tasks. returns the refcloud task id. the task is recorded as QUEUED and forwarded right away if the
    // connection has capacity; otherwise the reconciler forwards it, oldest first
    public String createTask(ComputePlatform platform, ComputeCaller caller, JsonNode request) {
        if (!caller.isRun()) {
            throw new ForbiddenException("researchers can't create TES tasks; submit work through this platform's WES");
        }
        ComputeConfig.Connection connection = connection(platform);
        TesTaskRules.validate(request, caller.runDirectory());
        TesTaskRules.Resources resources = TesTaskRules.resources(request, connection.limits(), platform);
        checkFitsPooledCapacity(resources, connection.limits());

        ComputeTask task = ComputeTask.builder()
                .id(UUID.randomUUID().toString())
                .computePlatformId(platform.getId())
                .userId(caller.userId())
                .runId(caller.runId())
                .state(ComputeTaskStates.QUEUED)
                .name(request.path("name").isString() ? request.get("name").stringValue() : null)
                .cpuCores(resources.cpuCores())
                .ramGb(resources.ramGb())
                .diskGb(resources.diskGb())
                .request(TesTaskRules.accepted(request, resources))
                .createdAt(Instant.now())
                .build();
        computeTaskRepository.save(task);

        forwardWaiting(platform.getConnectionKey());
        return task.getId();
    }

    // GET /tasks/{id}. a forwarded task still in flight is refreshed from the executor first (best effort: the
    // stored copy is served if the executor can't be reached)
    public ObjectNode getTask(ComputePlatform platform, ComputeCaller caller, String taskId, TesTaskViews.View view) {
        ComputeTask task = findVisible(platform, caller, taskId);
        if (task.getExecutorTaskId() != null && !ComputeTaskStates.isTerminal(task.getState())) {
            computeConnections.find(platform.getConnectionKey()).ifPresent(connection -> refresh(connection, task));
        }
        return TesTaskViews.render(task, view);
    }

    // GET /tasks: the caller's tasks, newest first. a researcher sees every task created on their behalf; a run
    // token sees only its run's tasks
    public TaskPage listTasks(
            ComputePlatform platform, ComputeCaller caller, TaskFilter filter, TesTaskViews.View view) {
        int pageSize = filter.pageSize() == null ? taskConfig.defaultPageSize() : filter.pageSize();
        if (pageSize < 1 || pageSize > taskConfig.maxPageSize()) {
            throw new BadRequestException("page_size must be between 1 and " + taskConfig.maxPageSize());
        }
        if (filter.state() != null && !ComputeTaskStates.ALL.contains(filter.state())) {
            throw new BadRequestException("unknown state: " + filter.state());
        }
        if (filter.tagValue() != null && filter.tagKey() == null) {
            throw new BadRequestException("tag_value needs a tag_key");
        }
        TesPageToken after = filter.pageToken() == null ? null : TesPageToken.decode(filter.pageToken());

        // one extra row says whether there's another page
        List<ComputeTask> rows = computeTaskRepository.findPage(
                platform.getId(),
                caller.isRun() ? null : caller.userId(),
                caller.isRun() ? caller.runId() : null,
                filter.state(),
                filter.namePrefix(),
                filter.tagKey(),
                filter.tagValue(),
                after == null ? null : after.createdAt(),
                after == null ? null : after.id(),
                pageSize + 1);
        boolean more = rows.size() > pageSize;
        List<ComputeTask> page = more ? rows.subList(0, pageSize) : rows;

        List<ObjectNode> tasks = new ArrayList<>();
        for (ComputeTask task : page) {
            tasks.add(TesTaskViews.render(task, view));
        }
        String nextPageToken =
                more ? TesPageToken.after(page.get(page.size() - 1)).encode() : null;
        return new TaskPage(tasks, nextPageToken);
    }

    // POST /tasks/{id}:cancel. a queued task that was never forwarded is canceled here; a forwarded one is canceled
    // in the executor, and the reconciler records its final state. canceling a finished task does nothing
    public void cancelTask(ComputePlatform platform, ComputeCaller caller, String taskId) {
        ComputeTask task = findVisible(platform, caller, taskId);
        if (ComputeTaskStates.isTerminal(task.getState())) {
            return;
        }
        ComputeConfig.Connection connection = connection(platform);
        ReentrantLock lock = lock(platform.getConnectionKey());
        lock.lock();
        try {
            // re-read under the lock: the task may have been forwarded since
            ComputeTask current = computeTaskRepository.findById(taskId).orElseThrow();
            if (current.getExecutorTaskId() == null) {
                current.setState(ComputeTaskStates.CANCELED);
                current.setEndedAt(Instant.now());
                current.setCoreHours(BigDecimal.ZERO);
                computeTaskRepository.save(current);
                return;
            }
            executorClient.cancelTask(connection, current.getExecutorTaskId());
            current.setState(ComputeTaskStates.CANCELING);
            computeTaskRepository.save(current);
        } finally {
            lock.unlock();
        }
    }

    // refreshes every forwarded task still in flight, then forwards waiting tasks while there's capacity, for every
    // configured connection
    @Scheduled(
            initialDelayString = "${ga4gh.refcloud.compute.tasks.reconcile-interval}",
            fixedDelayString = "${ga4gh.refcloud.compute.tasks.reconcile-interval}")
    public void reconcile() {
        for (String connectionKey : computeConnections.keys()) {
            ComputeConfig.Connection connection =
                    computeConnections.find(connectionKey).orElseThrow();
            for (ComputeTask task : computeTaskRepository.findInFlight(connectionKey, ComputeTaskStates.TERMINAL)) {
                refresh(connection, task);
            }
            forwardWaiting(connectionKey);
        }
    }

    // forwards waiting tasks in order, oldest first, while the connection's pooled limits allow. strict order: a
    // task that doesn't fit yet holds back the ones behind it, so no task waits forever behind smaller ones
    void forwardWaiting(String connectionKey) {
        Optional<ComputeConfig.Connection> found = computeConnections.find(connectionKey);
        if (found.isEmpty()) {
            return;
        }
        ComputeConfig.Connection connection = found.get();
        ComputeConfig.Limits limits = connection.limits();
        ReentrantLock lock = lock(connectionKey);
        lock.lock();
        try {
            List<ComputeTask> inFlight = computeTaskRepository.findInFlight(connectionKey, ComputeTaskStates.TERMINAL);
            int tasks = inFlight.size();
            int cpuCores = inFlight.stream().mapToInt(ComputeTask::getCpuCores).sum();
            BigDecimal ramGb = inFlight.stream().map(ComputeTask::getRamGb).reduce(BigDecimal.ZERO, BigDecimal::add);

            for (ComputeTask task : computeTaskRepository.findWaiting(connectionKey)) {
                boolean fits = tasks + 1 <= limits.maxTasksInFlight()
                        && cpuCores + task.getCpuCores() <= limits.maxCpuCoresInFlight()
                        && ramGb.add(task.getRamGb()).compareTo(limits.maxMemoryGbInFlight()) <= 0;
                if (!fits) {
                    return;
                }
                try {
                    String executorTaskId = executorClient.createTask(
                            connection,
                            TesTaskRules.forExecutor(
                                    task.getRequest(), task.getId(), task.getUserId(), task.getRunId()));
                    task.setExecutorTaskId(executorTaskId);
                    task.setForwardedAt(Instant.now());
                    computeTaskRepository.save(task);
                } catch (ExecutorException exception) {
                    // stays QUEUED; retried next round. a longer outage makes the platform unhealthy (503)
                    log.warn(
                            "could not forward task {} on connection {}: {}",
                            task.getId(),
                            connectionKey,
                            exception.getMessage());
                    return;
                }
                tasks++;
                cpuCores += task.getCpuCores();
                ramGb = ramGb.add(task.getRamGb());
            }
        } finally {
            lock.unlock();
        }
    }

    // copies the executor's latest task document and state, and records times and usage once it finishes
    void refresh(ComputeConfig.Connection connection, ComputeTask task) {
        JsonNode document;
        try {
            document = executorClient.getTask(connection, task.getExecutorTaskId());
        } catch (ExecutorException exception) {
            if (exception.status() == 404) {
                // the executor lost the task (e.g. its database was reset)
                task.setState(ComputeTaskStates.SYSTEM_ERROR);
                finish(task, Instant.now());
                computeTaskRepository.save(task);
                log.warn("task {} is gone from its executor; marked {}", task.getId(), ComputeTaskStates.SYSTEM_ERROR);
            } else {
                log.debug("could not refresh task {}: {}", task.getId(), exception.getMessage());
            }
            return;
        }
        applyExecutorDocument(task, document, Instant.now());
        computeTaskRepository.save(task);
    }

    // exposed for tests
    static void applyExecutorDocument(ComputeTask task, JsonNode document, Instant now) {
        String state = document.path("state").isString() ? document.get("state").stringValue() : "UNKNOWN";
        task.setState(ComputeTaskStates.ALL.contains(state) ? state : "UNKNOWN");
        task.setExecutorTask(document);

        // the latest attempt's times (TES task logs, one entry per attempt)
        JsonNode logs = document.path("logs");
        JsonNode lastAttempt = logs.isArray() && !logs.isEmpty() ? logs.get(logs.size() - 1) : null;
        Instant start = lastAttempt == null ? null : parseTime(lastAttempt.path("start_time"));
        Instant end = lastAttempt == null ? null : parseTime(lastAttempt.path("end_time"));
        if (start != null) {
            task.setStartedAt(start);
        }
        if (ComputeTaskStates.isTerminal(task.getState()) && task.getCoreHours() == null) {
            finish(task, end != null ? end : now);
        }
    }

    // usage is cpu cores x wall-clock hours from start to end; a task that never started used nothing
    private static void finish(ComputeTask task, Instant end) {
        task.setEndedAt(end);
        if (task.getStartedAt() == null || end.isBefore(task.getStartedAt())) {
            task.setCoreHours(BigDecimal.ZERO);
            return;
        }
        BigDecimal hours = BigDecimal.valueOf(
                        Duration.between(task.getStartedAt(), end).toMillis())
                .divide(BigDecimal.valueOf(3_600_000L), 6, RoundingMode.HALF_UP);
        task.setCoreHours(hours.multiply(BigDecimal.valueOf(task.getCpuCores())));
    }

    private static Instant parseTime(JsonNode value) {
        if (!value.isString() || value.stringValue().isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.stringValue());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    // a task that could never fit, even with nothing else running, would wait forever
    private static void checkFitsPooledCapacity(TesTaskRules.Resources resources, ComputeConfig.Limits limits) {
        if (resources.cpuCores() > limits.maxCpuCoresInFlight()
                || resources.ramGb().compareTo(limits.maxMemoryGbInFlight()) > 0) {
            throw new BadRequestException("the task's resources exceed this compute platform's total capacity");
        }
    }

    // a task is visible to the researcher it was created for, or to the run that created it; anyone else gets 404,
    // so other users' task ids can't be probed
    private ComputeTask findVisible(ComputePlatform platform, ComputeCaller caller, String taskId) {
        return computeTaskRepository
                .findByIdAndComputePlatformId(taskId, platform.getId())
                .filter(task -> caller.isRun()
                        ? caller.runId().equals(task.getRunId())
                        : caller.userId().equals(task.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("no task with ID '" + taskId + "'"));
    }

    private ComputeConfig.Connection connection(ComputePlatform platform) {
        // the availability filter already returns 503 for a platform whose connection is missing
        return computeConnections
                .find(platform.getConnectionKey())
                .orElseThrow(() -> new IllegalStateException(
                        "compute platform " + platform.getId() + " has no connection " + platform.getConnectionKey()));
    }

    private ReentrantLock lock(String connectionKey) {
        return forwardLocks.computeIfAbsent(connectionKey, key -> new ReentrantLock());
    }

    public record TaskFilter(
            String namePrefix, String state, String tagKey, String tagValue, Integer pageSize, String pageToken) {}

    public record TaskPage(List<ObjectNode> tasks, String nextPageToken) {}
}
