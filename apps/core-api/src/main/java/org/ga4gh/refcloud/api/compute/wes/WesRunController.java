package org.ga4gh.refcloud.api.compute.wes;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.ga4gh.refcloud.api.compute.ComputeCaller;
import org.ga4gh.refcloud.api.compute.ComputeCallerResolver;
import org.ga4gh.refcloud.api.compute.platform.ComputePlatform;
import org.ga4gh.refcloud.api.compute.serviceinfo.ComputeServiceInfoService;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

// WES 1.1.0 runs for one compute platform (Task 8). every call needs the platform's visa; each researcher sees only
// their own runs. unknown/disabled (404) and unhealthy (503) platforms are handled earlier, by
// ComputePlatformAvailabilityFilter, which lets a disabled platform's per-run paths through so owners keep their runs
// (option b); submitting and listing need an enabled platform
@RestController
@RequestMapping("/ga4gh/wes/{platformId}/v1")
@PreAuthorize("@GA4GHPassportTokenEvaluator.canAccessComputePlatform(authentication, #platformId)")
public class WesRunController {

    private static final MediaType TEXT_PLAIN_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final WesRunService wesRunService;

    private final ComputeServiceInfoService computeServiceInfoService;

    private final ComputeCallerResolver callerResolver;

    public WesRunController(
            WesRunService wesRunService,
            ComputeServiceInfoService computeServiceInfoService,
            ComputeCallerResolver callerResolver) {
        this.wesRunService = wesRunService;
        this.computeServiceInfoService = computeServiceInfoService;
        this.callerResolver = callerResolver;
    }

    // a WES RunRequest is multipart/form-data; workflow_url, workflow_type and workflow_type_version are required
    @PostMapping(
            value = "/runs",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<WesViews.RunId> runWorkflow(
            @PathVariable String platformId,
            @RequestParam(name = "workflow_url") String workflowUrl,
            @RequestParam(name = "workflow_type") String workflowType,
            @RequestParam(name = "workflow_type_version") String workflowTypeVersion,
            @RequestParam(name = "workflow_params", required = false) String workflowParams,
            @RequestParam(name = "tags", required = false) String tags,
            @RequestParam(name = "workflow_engine_parameters", required = false) String workflowEngineParameters,
            @RequestParam(name = "workflow_engine", required = false) String workflowEngine,
            @RequestParam(name = "workflow_engine_version", required = false) String workflowEngineVersion,
            HttpServletRequest request,
            Authentication authentication) {
        ComputeCaller caller = callerResolver.resolve(authentication);
        WesRunService.RunRequestForm form = new WesRunService.RunRequestForm(
                workflowParams,
                workflowType,
                workflowTypeVersion,
                tags,
                workflowEngineParameters,
                workflowEngine,
                workflowEngineVersion,
                workflowUrl,
                hasAttachment(request));
        String runId = wesRunService.submit(enabledPlatform(platformId), caller, form);
        return ResponseEntity.ok(new WesViews.RunId(runId));
    }

    @GetMapping("/runs")
    public ResponseEntity<WesViews.RunListResponse> listRuns(
            @PathVariable String platformId,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "page_token", required = false) String pageToken,
            Authentication authentication) {
        ComputeCaller caller = callerResolver.resolve(authentication);
        return ResponseEntity.ok(wesRunService.listRuns(enabledPlatform(platformId), caller, pageSize, pageToken));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<WesViews.RunLog> getRunLog(
            @PathVariable String platformId, @PathVariable String runId, Authentication authentication) {
        ComputeCaller caller = callerResolver.resolve(authentication);
        return ResponseEntity.ok(wesRunService.getRunLog(anyPlatform(platformId), caller, runId));
    }

    @GetMapping("/runs/{runId}/status")
    public ResponseEntity<WesViews.RunStatus> getRunStatus(
            @PathVariable String platformId, @PathVariable String runId, Authentication authentication) {
        ComputeCaller caller = callerResolver.resolve(authentication);
        return ResponseEntity.ok(wesRunService.getRunStatus(anyPlatform(platformId), caller, runId));
    }

    @GetMapping("/runs/{runId}/tasks")
    public ResponseEntity<WesViews.TaskListResponse> listTasks(
            @PathVariable String platformId,
            @PathVariable String runId,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "page_token", required = false) String pageToken,
            Authentication authentication) {
        ComputeCaller caller = callerResolver.resolve(authentication);
        return ResponseEntity.ok(wesRunService.listTasks(anyPlatform(platformId), caller, runId, pageSize, pageToken));
    }

    @PostMapping("/runs/{runId}/cancel")
    public ResponseEntity<WesViews.RunId> cancelRun(
            @PathVariable String platformId, @PathVariable String runId, Authentication authentication) {
        ComputeCaller caller = callerResolver.resolve(authentication);
        return ResponseEntity.ok(wesRunService.cancel(anyPlatform(platformId), caller, runId));
    }

    // a file under the run's outputs/ (D19, non-standard); {*path} is the rest of the url, e.g. /report/summary.txt
    @GetMapping("/runs/{runId}/outputs/{*path}")
    public ResponseEntity<Resource> getOutput(
            @PathVariable String platformId,
            @PathVariable String runId,
            @PathVariable String path,
            Authentication authentication) {
        ComputeCaller caller = callerResolver.resolve(authentication);
        Path file = wesRunService.outputFile(anyPlatform(platformId), caller, runId, path);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(String.valueOf(file.getFileName()))
                                .build()
                                .toString())
                .body(new FileSystemResource(file));
    }

    // the engine's console output (D19, non-standard): run_log.stdout and run_log.stderr point here
    @GetMapping("/runs/{runId}/logs/{stream}")
    public ResponseEntity<Resource> getLog(
            @PathVariable String platformId,
            @PathVariable String runId,
            @PathVariable String stream,
            Authentication authentication) {
        ComputeCaller caller = callerResolver.resolve(authentication);
        Resource body = wesRunService
                .logFile(anyPlatform(platformId), caller, runId, stream)
                .<Resource>map(FileSystemResource::new)
                .orElseGet(() -> new ByteArrayResource(new byte[0]));
        return ResponseEntity.ok().contentType(TEXT_PLAIN_UTF8).body(body);
    }

    // workflow_attachment can arrive as a file part or as a plain form field; either way it's refused
    private static boolean hasAttachment(HttpServletRequest request) {
        if (request.getParameter("workflow_attachment") != null) {
            return true;
        }
        return request instanceof MultipartHttpServletRequest multipart
                && !multipart.getFiles("workflow_attachment").isEmpty();
    }

    private ComputePlatform enabledPlatform(String platformId) {
        return computeServiceInfoService
                .findEnabledPlatform(platformId)
                .orElseThrow(() -> new ResourceNotFoundException("no compute platform with ID '" + platformId + "'"));
    }

    // per-run paths also work on a disabled platform, for the run's owner (option b)
    private ComputePlatform anyPlatform(String platformId) {
        return computeServiceInfoService
                .findPlatform(platformId)
                .orElseThrow(() -> new ResourceNotFoundException("no compute platform with ID '" + platformId + "'"));
    }
}
