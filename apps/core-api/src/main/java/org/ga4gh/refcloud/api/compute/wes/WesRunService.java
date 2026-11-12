package org.ga4gh.refcloud.api.compute.wes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.ga4gh.refcloud.api.compute.ComputeCaller;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputeConnections;
import org.ga4gh.refcloud.api.compute.ComputePageToken;
import org.ga4gh.refcloud.api.compute.ComputeWesConfig;
import org.ga4gh.refcloud.api.compute.platform.ComputePlatform;
import org.ga4gh.refcloud.api.compute.platformworkflow.ComputePlatformWorkflow;
import org.ga4gh.refcloud.api.compute.platformworkflow.ComputePlatformWorkflowRepository;
import org.ga4gh.refcloud.api.compute.platformworkflow.ComputeWorkflowSourceKind;
import org.ga4gh.refcloud.api.compute.run.ComputeRun;
import org.ga4gh.refcloud.api.compute.run.ComputeRunRepository;
import org.ga4gh.refcloud.api.compute.run.ComputeRunService;
import org.ga4gh.refcloud.api.compute.run.ComputeRunStates;
import org.ga4gh.refcloud.api.compute.task.ComputeTask;
import org.ga4gh.refcloud.api.compute.task.ComputeTaskRepository;
import org.ga4gh.refcloud.api.drs.DrsConfig;
import org.ga4gh.refcloud.api.drs.drsobject.DrsObjectService;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.ga4gh.refcloud.api.exception.ForbiddenException;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.ga4gh.refcloud.api.serviceregistry.ServiceRegistryConfig;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

// the refcloud WES API for one compute platform (Task 8). POST /runs makes quick checks only (all database work, no
// network), records the run QUEUED and returns; RunPreparationService then puts the workflow and DRS inputs in place in
// the background, and ComputeRunService (Task 7) starts it when pooled capacity allows. researchers see only their own
// runs; anyone else's run is reported as not found
@Service
public class WesRunService {

    // the engine's console output, written by the run launcher (Task 7)
    static final Map<String, String> LOG_FILES = Map.of("stdout", "engine.stdout.log", "stderr", "engine.stderr.log");

    private static final List<String> COUNTED_STATES = Stream.concat(
                    Stream.of(ComputeRunStates.QUEUED), ComputeRunStates.ACTIVE.stream())
            .toList();

    private final ComputeRunService computeRunService;

    private final ComputeRunRepository computeRunRepository;

    private final ComputePlatformWorkflowRepository workflowRepository;

    private final ComputeTaskRepository computeTaskRepository;

    private final ComputeConnections computeConnections;

    private final DrsObjectService drsObjectService;

    private final DrsConfig drsConfig;

    private final ComputeWesConfig wesConfig;

    private final RunPreparationService runPreparationService;

    private final ServiceRegistryConfig serviceRegistryConfig;

    private final ObjectMapper objectMapper;

    public WesRunService(
            ComputeRunService computeRunService,
            ComputeRunRepository computeRunRepository,
            ComputePlatformWorkflowRepository workflowRepository,
            ComputeTaskRepository computeTaskRepository,
            ComputeConnections computeConnections,
            DrsObjectService drsObjectService,
            DrsConfig drsConfig,
            ComputeWesConfig wesConfig,
            RunPreparationService runPreparationService,
            ServiceRegistryConfig serviceRegistryConfig,
            ObjectMapper objectMapper) {
        this.computeRunService = computeRunService;
        this.computeRunRepository = computeRunRepository;
        this.workflowRepository = workflowRepository;
        this.computeTaskRepository = computeTaskRepository;
        this.computeConnections = computeConnections;
        this.drsObjectService = drsObjectService;
        this.drsConfig = drsConfig;
        this.wesConfig = wesConfig;
        this.runPreparationService = runPreparationService;
        this.serviceRegistryConfig = serviceRegistryConfig;
        this.objectMapper = objectMapper;
    }

    // a WES RunRequest as received (multipart form fields; the JSON ones still as text)
    public record RunRequestForm(
            String workflowParams,
            String workflowType,
            String workflowTypeVersion,
            String tags,
            String workflowEngineParameters,
            String workflowEngine,
            String workflowEngineVersion,
            String workflowUrl,
            boolean hasAttachment) {}

    // POST /runs. the checks run in the spec's order (the visa was checked by @PreAuthorize); the first failure
    // rejects the run with a message saying what to change
    public String submit(ComputePlatform platform, ComputeCaller caller, RunRequestForm form) {
        requireResearcher(caller);
        ComputeConfig.Connection connection = connection(platform);

        // the allowlist (D20, D21): the workflow and its engine settings are the admin's, never the researcher's
        checkForm(form, objectMapper);
        ComputePlatformWorkflow entry = workflowRepository
                .findByPlatformAndWorkflowUrl(platform.getId(), form.workflowUrl())
                .orElseThrow(() -> new BadRequestException("workflow_url '" + form.workflowUrl()
                        + "' is not on this compute platform's workflow allowlist (it must match an entry exactly)"));
        checkEntry(entry, form, connection.type().engine(), connection.engine().nextflowVersion());
        JsonNode params = parseObject("workflow_params", form.workflowParams(), objectMapper);
        Map<String, String> tags = parseTags(form.tags(), objectMapper);

        // per-user limits (D11)
        checkConcurrentRuns(platform, caller.userId());
        checkMonthlyQuota(platform, connection, caller.userId(), Instant.now());

        // DRS inputs (D17)
        ArrayNode drsInputs = DrsInputs.check(
                params,
                drsConfig.hostDomain(),
                drsObjectService::getStagingInfo,
                objectId -> drsObjectService.validateUserIsAuthorizedForDrsObject(caller.userId(), objectId),
                connection.limits().maxRunInputsGb());

        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.set("workflow_params", params);
        request.put("workflow_type", form.workflowType());
        request.put("workflow_type_version", form.workflowTypeVersion());
        request.set("tags", objectMapper.valueToTree(tags));
        request.put("workflow_url", form.workflowUrl());
        if (hasText(form.workflowEngine())) {
            request.put("workflow_engine", form.workflowEngine());
        }
        if (hasText(form.workflowEngineVersion())) {
            request.put("workflow_engine_version", form.workflowEngineVersion());
        }

        ComputeRunService.RunDirectory directory;
        try {
            directory = computeRunService.allocateRunDirectory(platform);
        } catch (IOException exception) {
            throw new UncheckedIOException("could not create the run directory", exception);
        }
        String runId = computeRunService.submit(
                platform,
                caller.userId(),
                directory,
                new ComputeRunService.RunSubmission(
                        "workflow",
                        entry.getSourceKind() == ComputeWorkflowSourceKind.bundled ? null : entry.getGitMainScript(),
                        params,
                        entry.getEngineProfiles(),
                        entry.getEngineEnv(),
                        request,
                        entry.getId(),
                        workflowSource(entry),
                        drsInputs,
                        false));
        runPreparationService.prepareInBackground(runId);
        return runId;
    }

    // GET /runs: the caller's runs on this platform, newest first
    public WesViews.RunListResponse listRuns(
            ComputePlatform platform, ComputeCaller caller, Integer pageSize, String pageToken) {
        requireResearcher(caller);
        int size = pageSize(pageSize);
        ComputePageToken after = pageToken == null || pageToken.isEmpty() ? null : ComputePageToken.decode(pageToken);
        List<ComputeRun> page = computeRunRepository.findPage(
                platform.getId(),
                caller.userId(),
                after == null ? null : after.createdAt(),
                after == null ? null : after.id(),
                size + 1);
        boolean more = page.size() > size;
        List<WesViews.RunSummary> runs = page.stream()
                .limit(size)
                .map(run -> new WesViews.RunSummary(
                        run.getId(), run.getState(), run.getStartedAt(), run.getEndedAt(), tags(run)))
                .toList();
        ComputeRun last = more ? page.get(size - 1) : null;
        return new WesViews.RunListResponse(
                runs, last == null ? "" : new ComputePageToken(last.getCreatedAt(), last.getId()).encode());
    }

    public WesViews.RunLog getRunLog(ComputePlatform platform, ComputeCaller caller, String runId) {
        ComputeRun run = ownedRun(platform, caller, runId);
        String runUrl = runUrl(run);
        WesViews.Log log = new WesViews.Log(
                run.getWorkflowSource() == null
                        ? null
                        : run.getWorkflowSource().path("name").asString(null),
                null,
                run.getStartedAt(),
                run.getEndedAt(),
                runUrl + "/logs/stdout",
                runUrl + "/logs/stderr",
                run.getExitCode(),
                run.getStateReason() == null ? List.of() : List.of(run.getStateReason()));
        return new WesViews.RunLog(
                run.getId(), run.getRequest(), run.getState(), log, runUrl + "/tasks", outputs(run, runUrl));
    }

    public WesViews.RunStatus getRunStatus(ComputePlatform platform, ComputeCaller caller, String runId) {
        ComputeRun run = ownedRun(platform, caller, runId);
        return new WesViews.RunStatus(run.getId(), run.getState());
    }

    // GET /runs/{run_id}/tasks: the run's TES tasks, newest first
    public WesViews.TaskListResponse listTasks(
            ComputePlatform platform, ComputeCaller caller, String runId, Integer pageSize, String pageToken) {
        ComputeRun run = ownedRun(platform, caller, runId);
        int size = pageSize(pageSize);
        ComputePageToken after = pageToken == null || pageToken.isEmpty() ? null : ComputePageToken.decode(pageToken);
        List<ComputeTask> page = computeTaskRepository.findPage(
                platform.getId(),
                null,
                run.getId(),
                null,
                null,
                null,
                null,
                after == null ? null : after.createdAt(),
                after == null ? null : after.id(),
                size + 1);
        boolean more = page.size() > size;
        String tesBase = baseUrl() + "/ga4gh/tes/" + platform.getId() + "/v1/tasks/";
        List<WesViews.TaskLog> taskLogs =
                page.stream().limit(size).map(task -> taskLog(task, tesBase)).toList();
        ComputeTask last = more ? page.get(size - 1) : null;
        return new WesViews.TaskListResponse(
                taskLogs, last == null ? "" : new ComputePageToken(last.getCreatedAt(), last.getId()).encode());
    }

    public WesViews.RunId cancel(ComputePlatform platform, ComputeCaller caller, String runId) {
        ComputeRun run = ownedRun(platform, caller, runId);
        computeRunService.cancel(run.getId(), "canceled by its owner");
        return new WesViews.RunId(run.getId());
    }

    // GET /runs/{run_id}/outputs/{path}: a file strictly inside the run's outputs/ directory
    public Path outputFile(ComputePlatform platform, ComputeCaller caller, String runId, String path) {
        ComputeRun run = ownedRun(platform, caller, runId);
        Path outputs = Path.of(run.getRunDirectory()).resolve("outputs");
        return resolveInside(outputs, path)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "the run has no output '" + (path.startsWith("/") ? path.substring(1) : path) + "'"));
    }

    // GET /runs/{run_id}/logs/{stdout|stderr}: the engine's console output, empty until the run starts
    public Optional<Path> logFile(ComputePlatform platform, ComputeCaller caller, String runId, String stream) {
        ComputeRun run = ownedRun(platform, caller, runId);
        String name = LOG_FILES.get(stream);
        if (name == null) {
            throw new ResourceNotFoundException("no log '" + stream + "' (use stdout or stderr)");
        }
        Path file = Path.of(run.getRunDirectory()).resolve(name);
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? Optional.of(file) : Optional.empty();
    }

    // the request's own shape: the required fields, and nothing that would change what or how the engine runs
    static void checkForm(RunRequestForm form, ObjectMapper objectMapper) {
        requireText("workflow_url", form.workflowUrl());
        requireText("workflow_type", form.workflowType());
        requireText("workflow_type_version", form.workflowTypeVersion());
        if (form.hasAttachment()) {
            throw new BadRequestException(
                    "workflow_attachment is not supported: workflows come from this compute platform's allowlist");
        }
        if (!parseObject("workflow_engine_parameters", form.workflowEngineParameters(), objectMapper)
                .isEmpty()) {
            throw new BadRequestException("workflow_engine_parameters are not supported: the engine runs with the"
                    + " settings of the workflow's allowlist entry");
        }
    }

    // the request must describe the allowlisted workflow, and any engine it names must be the connection's
    static void checkEntry(ComputePlatformWorkflow entry, RunRequestForm form, String engine, String engineVersion) {
        if (!entry.getWorkflowType().equals(form.workflowType())
                || !entry.getWorkflowTypeVersion().equals(form.workflowTypeVersion())) {
            throw new BadRequestException("this workflow's workflow_type / workflow_type_version is "
                    + entry.getWorkflowType() + " / " + entry.getWorkflowTypeVersion() + ", not "
                    + form.workflowType() + " / " + form.workflowTypeVersion());
        }
        if (hasText(form.workflowEngine()) && !engine.equalsIgnoreCase(form.workflowEngine())) {
            throw new BadRequestException(
                    "this compute platform runs workflows with " + engine + ", not " + form.workflowEngine());
        }
        if (hasText(form.workflowEngineVersion()) && !engineVersion.equals(form.workflowEngineVersion())) {
            throw new BadRequestException("this compute platform runs " + engine + " " + engineVersion + ", not "
                    + form.workflowEngineVersion());
        }
    }

    private void checkConcurrentRuns(ComputePlatform platform, String userId) {
        Integer limit = platform.getUserMaxConcurrentRuns();
        if (limit == null) {
            return;
        }
        long runs = computeRunRepository.countByComputePlatformIdAndUserIdAndStateIn(
                platform.getId(), userId, COUNTED_STATES);
        if (runs >= limit) {
            throw new ForbiddenException("you have " + runs + " runs queued or running on this compute platform, its"
                    + " limit per user; submit again when one has finished");
        }
    }

    // core-hours from tasks that finished this calendar month (UTC), against the user's quota on this platform and
    // the connection's pooled budget (D11). a run that crosses a quota finishes; the next one is refused
    private void checkMonthlyQuota(
            ComputePlatform platform, ComputeConfig.Connection connection, String userId, Instant now) {
        Instant monthStart = startOfMonth(now);
        BigDecimal userQuota = platform.getUserMonthlyCoreHours();
        BigDecimal budget = connection.limits().monthlyCoreHoursBudget();
        checkMonthlyUsage(
                userQuota == null
                        ? null
                        : computeTaskRepository.sumUserCoreHoursSince(platform.getId(), userId, monthStart),
                userQuota,
                budget == null
                        ? null
                        : computeTaskRepository.sumConnectionCoreHoursSince(platform.getConnectionKey(), monthStart),
                budget,
                startOfNextMonth(now));
    }

    // a null quota or budget isn't set, so it isn't checked
    static void checkMonthlyUsage(
            BigDecimal userUsed,
            BigDecimal userQuota,
            BigDecimal pooledUsed,
            BigDecimal pooledBudget,
            Instant resetsAt) {
        if (userQuota != null && userUsed.compareTo(userQuota) >= 0) {
            throw new ForbiddenException("you have used " + hours(userUsed) + " of your monthly quota of "
                    + hours(userQuota) + " core-hours on this compute platform; it resets at " + resetsAt);
        }
        if (pooledBudget != null && pooledUsed.compareTo(pooledBudget) >= 0) {
            throw new ForbiddenException("this compute platform's shared monthly budget of " + hours(pooledBudget)
                    + " core-hours has been used; it resets at " + resetsAt);
        }
    }

    static Instant startOfMonth(Instant now) {
        return YearMonth.from(now.atOffset(ZoneOffset.UTC))
                .atDay(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }

    static Instant startOfNextMonth(Instant now) {
        return YearMonth.from(now.atOffset(ZoneOffset.UTC))
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }

    // a snapshot of where the workflow comes from, so preparation (and a retry after a restart) doesn't depend on
    // the allowlist entry still existing
    private static ObjectNode workflowSource(ComputePlatformWorkflow entry) {
        ObjectNode source = JsonNodeFactory.instance.objectNode();
        source.put("kind", entry.getSourceKind().name());
        source.put("name", entry.getName());
        if (entry.getSourceKind() == ComputeWorkflowSourceKind.bundled) {
            source.put("bundled_path", entry.getBundledPath());
        } else {
            source.put("repository", entry.getGitRepositoryUrl());
            source.put("commit", entry.getGitCommit());
        }
        if (entry.getSourceKind() == ComputeWorkflowSourceKind.trs) {
            source.put("trs_server_url", entry.getTrsServerUrl());
            source.put("trs_tool_id", entry.getTrsToolId());
            source.put("trs_version_id", entry.getTrsVersionId());
        }
        return source;
    }

    // the caller's run on this platform; anyone else's run doesn't exist as far as they can tell
    private ComputeRun ownedRun(ComputePlatform platform, ComputeCaller caller, String runId) {
        requireResearcher(caller);
        return computeRunRepository
                .findById(runId)
                .filter(run -> run.getComputePlatformId().equals(platform.getId()))
                .filter(run -> run.getUserId().equals(caller.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("no run with ID '" + runId + "'"));
    }

    // WES is for researchers; run tokens only ever reach TES, but a run must never act through WES
    private static void requireResearcher(ComputeCaller caller) {
        if (caller.isRun()) {
            throw new ForbiddenException("workflow runs can't use WES");
        }
    }

    private WesViews.Outputs outputs(ComputeRun run, String runUrl) {
        Path outputs = Path.of(run.getRunDirectory()).resolve("outputs");
        if (!Files.isDirectory(outputs, LinkOption.NOFOLLOW_LINKS)) {
            return new WesViews.Outputs(List.of());
        }
        // walk doesn't follow symlinks, and only regular files are listed
        try (Stream<Path> files = Files.walk(outputs)) {
            List<WesViews.OutputFile> listed = files.filter(
                            file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    .map(file -> {
                        String relative = outputs.relativize(file).toString();
                        return new WesViews.OutputFile(
                                relative, size(file), runUrl + "/outputs/" + encodePath(relative));
                    })
                    .sorted(Comparator.comparing(WesViews.OutputFile::path))
                    .toList();
            return new WesViews.Outputs(listed);
        } catch (IOException exception) {
            throw new UncheckedIOException("could not list the run's outputs", exception);
        }
    }

    // resolves a relative path strictly inside root: no absolute paths, no "." or ".." segments, and (after symlinks
    // are followed) still inside root. empty if it isn't, or isn't a regular file
    static Optional<Path> resolveInside(Path root, String relative) {
        String path = relative.startsWith("/") ? relative.substring(1) : relative;
        if (path.isEmpty() || path.indexOf('\0') >= 0 || path.startsWith("/") || path.contains("\\")) {
            return Optional.empty();
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return Optional.empty();
            }
        }
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path candidate = normalizedRoot.resolve(path).normalize();
            if (!candidate.startsWith(normalizedRoot) || !Files.isRegularFile(candidate)) {
                return Optional.empty();
            }
            Path real = candidate.toRealPath();
            return real.startsWith(normalizedRoot.toRealPath()) ? Optional.of(real) : Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    // never the executor's system_logs: funnel's are its internal log lines (its docker run command, host paths,
    // container ids), which the TES views drop too (Task 6)
    static WesViews.TaskLog taskLog(ComputeTask task, String tesBase) {
        JsonNode executorLogs = task.getExecutorTask() == null
                ? JsonNodeFactory.instance.arrayNode()
                : task.getExecutorTask().path("logs");
        // the last attempt's last executor exit code
        Integer exitCode = null;
        for (JsonNode attempt : executorLogs) {
            for (JsonNode executorLog : attempt.path("logs")) {
                if (executorLog.path("exit_code").isNumber()) {
                    exitCode = executorLog.path("exit_code").intValue();
                }
            }
        }
        JsonNode command = task.getRequest() == null
                ? null
                : task.getRequest().path("executors").path(0).path("command");
        List<String> cmd = command == null || !command.isArray()
                ? null
                : StreamSupport.stream(command.spliterator(), false)
                        .map(JsonNode::asString)
                        .toList();
        return new WesViews.TaskLog(
                task.getId(),
                task.getName() == null ? task.getId() : task.getName(),
                cmd,
                task.getStartedAt(),
                task.getEndedAt(),
                exitCode,
                tesBase + task.getId());
    }

    private Map<String, String> tags(ComputeRun run) {
        Map<String, String> tags = new LinkedHashMap<>();
        if (run.getRequest() != null) {
            JsonNode node = run.getRequest().path("tags");
            for (String name : node.propertyNames()) {
                tags.put(name, node.get(name).asString());
            }
        }
        return tags;
    }

    // a JSON object form field; absent or blank is an empty object
    static JsonNode parseObject(String field, String text, ObjectMapper objectMapper) {
        if (!hasText(text)) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(text);
            if (parsed == null || !parsed.isObject()) {
                throw new BadRequestException(field + " must be a JSON object");
            }
            return parsed;
        } catch (JacksonException exception) {
            throw new BadRequestException(field + " is not valid JSON");
        }
    }

    static Map<String, String> parseTags(String text, ObjectMapper objectMapper) {
        JsonNode tags = parseObject("tags", text, objectMapper);
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String name : tags.propertyNames()) {
            if (!tags.get(name).isString()) {
                throw new BadRequestException("tags must map names to strings");
            }
            parsed.put(name, tags.get(name).stringValue());
        }
        return parsed;
    }

    private int pageSize(Integer requested) {
        if (requested == null) {
            return wesConfig.defaultPageSize();
        }
        if (requested < 1) {
            throw new BadRequestException("page_size must be at least 1");
        }
        return Math.min(requested, wesConfig.maxPageSize());
    }

    private String runUrl(ComputeRun run) {
        return baseUrl() + "/ga4gh/wes/" + run.getComputePlatformId() + "/v1/runs/" + run.getId();
    }

    private String baseUrl() {
        return serviceRegistryConfig.baseUrlWithoutTrailingSlash();
    }

    private ComputeConfig.Connection connection(ComputePlatform platform) {
        return computeConnections
                .find(platform.getConnectionKey())
                .orElseThrow(() -> new IllegalStateException(
                        "compute platform " + platform.getId() + " has no connection " + platform.getConnectionKey()));
    }

    // core-hours to two decimal places, e.g. 12.50
    private static String hours(BigDecimal coreHours) {
        return coreHours.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    // each path segment percent-encoded (spaces as %20, not +)
    static String encodePath(String relative) {
        return Stream.of(relative.split("/"))
                .map(segment ->
                        URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private static void requireText(String field, String value) {
        if (!hasText(value)) {
            throw new BadRequestException(field + " is required");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
