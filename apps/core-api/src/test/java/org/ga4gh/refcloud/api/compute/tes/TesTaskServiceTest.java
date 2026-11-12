package org.ga4gh.refcloud.api.compute.tes;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;

import java.math.BigDecimal;
import java.time.Instant;
import org.ga4gh.refcloud.api.compute.task.ComputeTask;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// how executor task documents become state, times and usage, and page tokens
public class TesTaskServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private static ComputeTask task(int cpuCores) {
        return ComputeTask.builder()
                .id("t")
                .cpuCores(cpuCores)
                .ramGb(BigDecimal.ONE)
                .build();
    }

    @Test
    public void runningTaskRecordsItsStartButNoUsage() {
        ComputeTask task = task(2);
        TesTaskService.applyExecutorDocument(
                task,
                objectMapper.readTree("{\"state\":\"RUNNING\",\"logs\":[{\"start_time\":\"2026-11-12T10:00:00Z\"}]}"),
                Instant.parse("2026-11-12T10:30:00Z"));

        assertEquals(task.getState(), "RUNNING");
        assertEquals(task.getStartedAt(), Instant.parse("2026-11-12T10:00:00Z"));
        assertNull(task.getCoreHours());
    }

    @Test
    public void finishedTaskRecordsCoreHoursFromItsLog() {
        ComputeTask task = task(2);
        TesTaskService.applyExecutorDocument(task, objectMapper.readTree("""
                        {"state":"COMPLETE","logs":[{"start_time":"2026-11-12T10:00:00.5Z",
                                                     "end_time":"2026-11-12T11:30:00.5Z"}]}
                        """), Instant.parse("2026-11-12T12:00:00Z"));

        assertEquals(task.getState(), "COMPLETE");
        assertEquals(task.getEndedAt(), Instant.parse("2026-11-12T11:30:00.5Z"));
        // 2 cores x 1.5 hours
        assertEquals(
                task.getCoreHours().compareTo(new BigDecimal("3")),
                0,
                task.getCoreHours().toString());
    }

    @Test
    public void usageUsesTheLatestAttempt() {
        ComputeTask task = task(1);
        TesTaskService.applyExecutorDocument(task, objectMapper.readTree("""
                        {"state":"COMPLETE","logs":[
                          {"start_time":"2026-11-12T09:00:00Z","end_time":"2026-11-12T09:10:00Z"},
                          {"start_time":"2026-11-12T10:00:00Z","end_time":"2026-11-12T10:30:00Z"}]}
                        """), Instant.now());

        assertEquals(
                task.getCoreHours().compareTo(new BigDecimal("0.5")),
                0,
                task.getCoreHours().toString());
    }

    @Test
    public void taskCanceledBeforeStartingUsedNothing() {
        ComputeTask task = task(4);
        TesTaskService.applyExecutorDocument(
                task, objectMapper.readTree("{\"state\":\"CANCELED\"}"), Instant.parse("2026-11-12T12:00:00Z"));

        assertEquals(task.getCoreHours(), BigDecimal.ZERO);
        assertEquals(task.getEndedAt(), Instant.parse("2026-11-12T12:00:00Z"));
    }

    @Test
    public void unknownExecutorStateIsUnknown() {
        ComputeTask task = task(1);
        TesTaskService.applyExecutorDocument(task, objectMapper.readTree("{\"state\":\"WEIRD\"}"), Instant.now());

        assertEquals(task.getState(), "UNKNOWN");
    }

    @Test
    public void usageIsRecordedOnce() {
        ComputeTask task = task(1);
        String finished = "{\"state\":\"COMPLETE\",\"logs\":[{\"start_time\":\"2026-11-12T10:00:00Z\","
                + "\"end_time\":\"2026-11-12T11:00:00Z\"}]}";
        TesTaskService.applyExecutorDocument(task, objectMapper.readTree(finished), Instant.now());
        BigDecimal first = task.getCoreHours();
        TesTaskService.applyExecutorDocument(task, objectMapper.readTree(finished), Instant.now());

        assertEquals(task.getCoreHours(), first);
    }

    @Test
    public void pageTokenRoundTrips() {
        ComputeTask task = ComputeTask.builder()
                .id("abc|def")
                .createdAt(Instant.parse("2026-11-12T10:00:00.123456Z"))
                .build();

        TesPageToken decoded = TesPageToken.decode(TesPageToken.after(task).encode());

        assertEquals(decoded.createdAt(), task.getCreatedAt());
        assertEquals(decoded.id(), "abc|def");
    }

    @Test
    public void invalidPageTokenIsABadRequest() {
        expectThrows(BadRequestException.class, () -> TesPageToken.decode("not a token"));
        expectThrows(BadRequestException.class, () -> TesPageToken.decode("bm8tc2VwYXJhdG9y"));
    }
}
