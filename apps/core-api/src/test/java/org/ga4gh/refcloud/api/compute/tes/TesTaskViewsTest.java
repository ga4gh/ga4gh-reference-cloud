package org.ga4gh.refcloud.api.compute.tes;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.ga4gh.refcloud.api.compute.task.ComputeTask;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class TesTaskViewsTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private ComputeTask task(String executorTaskJson) {
        return ComputeTask.builder()
                .id("refcloud-task-1")
                .state("RUNNING")
                .createdAt(Instant.parse("2026-11-12T10:00:00Z"))
                .cpuCores(1)
                .ramGb(BigDecimal.ONE)
                .request(objectMapper.readTree("""
                        {"name":"t1","executors":[{"image":"ubuntu","command":["echo"]}],
                         "tags":{"sample":"s1"},"inputs":[{"content":"hello","path":"/in/h"}]}
                        """))
                .executorTask(executorTaskJson == null ? null : objectMapper.readTree(executorTaskJson))
                .build();
    }

    private static final String EXECUTOR_TASK = """
            {"id":"funnel-id","state":"COMPLETE","name":"t1","executors":[{"image":"ubuntu","command":["echo"]}],
             "tags":{"sample":"s1","refcloud_task_id":"refcloud-task-1","refcloud_user":"u1"},
             "inputs":[{"content":"hello","path":"/in/h"}],
             "logs":[{"start_time":"2026-11-12T10:00:01Z","system_logs":["internal"],
                      "logs":[{"stdout":"hi","stderr":"","exit_code":0}]}]}
            """;

    @Test
    public void minimalIsIdAndState() {
        ObjectNode view = TesTaskViews.render(task(EXECUTOR_TASK), TesTaskViews.View.MINIMAL);

        assertEquals(view.propertyNames().stream().sorted().toList(), List.of("id", "state"));
        assertEquals(view.get("id").asString(), "refcloud-task-1");
    }

    @Test
    public void fullUsesTheRefcloudIdAndStateAndHidesInternalTags() {
        ObjectNode view = TesTaskViews.render(task(EXECUTOR_TASK), TesTaskViews.View.FULL);

        assertEquals(view.get("id").asString(), "refcloud-task-1");
        // the refcloud's state, not the executor document's (they converge on the next refresh)
        assertEquals(view.get("state").asString(), "RUNNING");
        assertEquals(view.get("creation_time").asString(), "2026-11-12T10:00:00Z");
        assertEquals(view.at("/tags/sample").asString(), "s1");
        assertFalse(view.get("tags").has("refcloud_task_id"));
        assertFalse(view.get("tags").has("refcloud_user"));
        assertFalse(view.toString().contains("funnel-id"), "the executor's task id must never be exposed");
        assertEquals(view.at("/logs/0/logs/0/stdout").asString(), "hi");
        assertEquals(view.at("/inputs/0/content").asString(), "hello");
        // the executor's internal logs are never exposed
        assertFalse(view.at("/logs/0").has("system_logs"));
    }

    @Test
    public void basicDropsLargeFields() {
        ObjectNode view = TesTaskViews.render(task(EXECUTOR_TASK), TesTaskViews.View.BASIC);

        assertFalse(view.at("/inputs/0").has("content"));
        assertFalse(view.at("/logs/0").has("system_logs"));
        assertFalse(view.at("/logs/0/logs/0").has("stdout"));
        assertFalse(view.at("/logs/0/logs/0").has("stderr"));
        assertEquals(view.at("/logs/0/logs/0/exit_code").asInt(), 0);
    }

    @Test
    public void queuedTaskRendersFromTheAcceptedRequest() {
        ObjectNode view = TesTaskViews.render(task(null), TesTaskViews.View.FULL);

        assertEquals(view.get("id").asString(), "refcloud-task-1");
        assertEquals(view.get("name").asString(), "t1");
        assertTrue(view.has("executors"));
    }

    @Test
    public void viewParsing() {
        assertEquals(TesTaskViews.parseView(null), TesTaskViews.View.MINIMAL);
        assertEquals(TesTaskViews.parseView("FULL"), TesTaskViews.View.FULL);
        expectThrows(BadRequestException.class, () -> TesTaskViews.parseView("full"));
    }
}
