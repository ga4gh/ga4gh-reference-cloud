package org.ga4gh.refcloud.api.compute.tes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import org.ga4gh.refcloud.api.compute.ComputeCallerResolver;
import org.ga4gh.refcloud.api.compute.platform.ComputePlatform;
import org.ga4gh.refcloud.api.compute.serviceinfo.ComputeServiceInfoService;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

// TES 1.1 tasks for one compute platform (Task 6). every call needs the platform's visa (researchers) or a run token
// for this platform (Task 7). researchers are read-only: they list, get and cancel the tasks created on their
// behalf; only workflow runs create tasks. unknown/disabled (404) and unhealthy (503) platforms are handled earlier,
// by ComputePlatformAvailabilityFilter
@RestController
@RequestMapping("/ga4gh/tes/{platformId}/v1")
@PreAuthorize("@GA4GHPassportTokenEvaluator.canAccessComputePlatform(authentication, #platformId)")
public class TesTaskController {

    private final TesTaskService tesTaskService;

    private final ComputeServiceInfoService computeServiceInfoService;

    private final ComputeCallerResolver callerResolver;

    public TesTaskController(
            TesTaskService tesTaskService,
            ComputeServiceInfoService computeServiceInfoService,
            ComputeCallerResolver callerResolver) {
        this.tesTaskService = tesTaskService;
        this.computeServiceInfoService = computeServiceInfoService;
        this.callerResolver = callerResolver;
    }

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, String>> createTask(
            @PathVariable String platformId, @RequestBody JsonNode task, Authentication authentication) {
        String id = tesTaskService.createTask(platform(platformId), callerResolver.resolve(authentication), task);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @GetMapping("/tasks")
    public ResponseEntity<TaskListResponse> listTasks(
            @PathVariable String platformId,
            @RequestParam(name = "name_prefix", required = false) String namePrefix,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "tag_key", required = false) List<String> tagKeys,
            @RequestParam(name = "tag_value", required = false) List<String> tagValues,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "page_token", required = false) String pageToken,
            @RequestParam(name = "view", required = false) String view,
            Authentication authentication) {
        // one tag_key/tag_value pair is supported
        if ((tagKeys != null && tagKeys.size() > 1) || (tagValues != null && tagValues.size() > 1)) {
            throw new BadRequestException("only one tag_key / tag_value pair is supported");
        }
        TesTaskService.TaskFilter filter = new TesTaskService.TaskFilter(
                namePrefix,
                state,
                tagKeys == null || tagKeys.isEmpty() ? null : tagKeys.get(0),
                tagValues == null || tagValues.isEmpty() ? null : tagValues.get(0),
                pageSize,
                pageToken);
        TesTaskService.TaskPage page = tesTaskService.listTasks(
                platform(platformId), callerResolver.resolve(authentication), filter, TesTaskViews.parseView(view));
        return ResponseEntity.ok(new TaskListResponse(page.tasks(), page.nextPageToken()));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<ObjectNode> getTask(
            @PathVariable String platformId,
            @PathVariable String id,
            @RequestParam(name = "view", required = false) String view,
            Authentication authentication) {
        return ResponseEntity.ok(tesTaskService.getTask(
                platform(platformId), callerResolver.resolve(authentication), id, TesTaskViews.parseView(view)));
    }

    @PostMapping("/tasks/{id}:cancel")
    public ResponseEntity<Map<String, String>> cancelTask(
            @PathVariable String platformId, @PathVariable String id, Authentication authentication) {
        tesTaskService.cancelTask(platform(platformId), callerResolver.resolve(authentication), id);
        return ResponseEntity.ok(Map.of());
    }

    private ComputePlatform platform(String platformId) {
        return computeServiceInfoService
                .findEnabledPlatform(platformId)
                .orElseThrow(() -> new ResourceNotFoundException("no compute platform with ID '" + platformId + "'"));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TaskListResponse(
            List<ObjectNode> tasks,
            @JsonProperty("next_page_token") String nextPageToken) {}
}
