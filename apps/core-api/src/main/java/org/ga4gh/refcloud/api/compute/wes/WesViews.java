package org.ga4gh.refcloud.api.compute.wes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

// the WES 1.1.0 response shapes (snake_case, as the WES spec names them). fields WES marks optional are left out when
// there's nothing to say
public final class WesViews {

    private WesViews() {}

    public record RunId(@JsonProperty("run_id") String runId) {}

    public record RunStatus(@JsonProperty("run_id") String runId, String state) {}

    // a run in GET /runs; tags is required by WES, so it's always present (possibly empty)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunSummary(
            @JsonProperty("run_id") String runId,
            String state,
            @JsonProperty("start_time") Instant startTime,
            @JsonProperty("end_time") Instant endTime,
            Map<String, String> tags) {}

    // next_page_token is "" on the last page, as WES specifies
    public record RunListResponse(
            List<RunSummary> runs,
            @JsonProperty("next_page_token") String nextPageToken) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunLog(
            @JsonProperty("run_id") String runId,
            JsonNode request,
            String state,
            @JsonProperty("run_log") Log runLog,
            @JsonProperty("task_logs_url") String taskLogsUrl,
            Outputs outputs) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Log(
            String name,
            List<String> cmd,
            @JsonProperty("start_time") Instant startTime,
            @JsonProperty("end_time") Instant endTime,
            String stdout,
            String stderr,
            @JsonProperty("exit_code") Integer exitCode,
            @JsonProperty("system_logs") List<String> systemLogs) {}

    // a run's TES task, with its refcloud TES url. no system_logs: the executor's are internal (Task 6)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TaskLog(
            String id,
            String name,
            List<String> cmd,
            @JsonProperty("start_time") Instant startTime,
            @JsonProperty("end_time") Instant endTime,
            @JsonProperty("exit_code") Integer exitCode,
            @JsonProperty("tes_uri") String tesUri) {}

    public record TaskListResponse(
            @JsonProperty("task_logs") List<TaskLog> taskLogs,
            @JsonProperty("next_page_token") String nextPageToken) {}

    // RunLog.outputs (D19): every file under the run's outputs/ directory, with a download url
    public record Outputs(List<OutputFile> files) {}

    public record OutputFile(String path, long size, String url) {}
}
