package org.ga4gh.refcloud.api.compute.wes;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.ga4gh.refcloud.api.compute.platformworkflow.ComputePlatformWorkflow;
import org.ga4gh.refcloud.api.compute.task.ComputeTask;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.ga4gh.refcloud.api.exception.ForbiddenException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class WesRunServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private Path directory;

    @BeforeMethod
    public void createDirectory() throws IOException {
        directory = Files.createTempDirectory("wes-run-service-test");
    }

    @AfterMethod(alwaysRun = true)
    public void deleteDirectory() throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static WesRunService.RunRequestForm form(
            String url, String type, String version, String engineParameters, boolean attachment) {
        return new WesRunService.RunRequestForm(
                "{}", type, version, null, engineParameters, null, null, url, attachment);
    }

    private static WesRunService.RunRequestForm engineForm(String engine, String engineVersion) {
        return new WesRunService.RunRequestForm(
                null, "NFL", "DSL2", null, null, engine, engineVersion, "refcloud:bundled/drs-checksum", false);
    }

    private static ComputePlatformWorkflow entry() {
        return ComputePlatformWorkflow.builder()
                .workflowUrl("refcloud:bundled/drs-checksum")
                .workflowType("NFL")
                .workflowTypeVersion("DSL2")
                .build();
    }

    @Test
    public void quotaMonthIsTheUtcCalendarMonth() {
        Instant now = Instant.parse("2026-11-12T15:30:00Z");

        assertEquals(WesRunService.startOfMonth(now), Instant.parse("2026-11-01T00:00:00Z"));
        assertEquals(WesRunService.startOfNextMonth(now), Instant.parse("2026-12-01T00:00:00Z"));
        // december rolls over into the next year
        assertEquals(
                WesRunService.startOfNextMonth(Instant.parse("2026-12-31T23:59:59Z")),
                Instant.parse("2027-01-01T00:00:00Z"));
        assertEquals(
                WesRunService.startOfMonth(Instant.parse("2026-12-01T00:00:00Z")),
                Instant.parse("2026-12-01T00:00:00Z"));
    }

    @Test
    public void monthlyUsageIsRefusedOnceAQuotaIsUsedUp() {
        Instant resets = Instant.parse("2026-12-01T00:00:00Z");

        // under both, or neither set
        WesRunService.checkMonthlyUsage(
                new BigDecimal("9.99"), BigDecimal.TEN, new BigDecimal("50"), new BigDecimal("100"), resets);
        WesRunService.checkMonthlyUsage(null, null, null, null, resets);

        ForbiddenException user = expectThrows(
                ForbiddenException.class,
                () -> WesRunService.checkMonthlyUsage(BigDecimal.TEN, BigDecimal.TEN, null, null, resets));
        assertEquals(
                user.getMessage(),
                "you have used 10.00 of your monthly quota of 10.00 core-hours on this compute platform; it resets at"
                        + " 2026-12-01T00:00:00Z");

        ForbiddenException pooled = expectThrows(
                ForbiddenException.class,
                () -> WesRunService.checkMonthlyUsage(
                        BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("100.5"), new BigDecimal("100"), resets));
        assertTrue(pooled.getMessage().startsWith("this compute platform's shared monthly budget of 100.00"));
    }

    @Test
    public void requestsNeedTheRequiredFieldsAndNoAttachmentsOrEngineParameters() {
        WesRunService.checkForm(form("u", "NFL", "DSL2", null, false), objectMapper);
        // an empty engine parameters object is the same as none
        WesRunService.checkForm(form("u", "NFL", "DSL2", "{}", false), objectMapper);

        expectThrows(
                BadRequestException.class,
                () -> WesRunService.checkForm(form(" ", "NFL", "DSL2", null, false), objectMapper));
        expectThrows(
                BadRequestException.class,
                () -> WesRunService.checkForm(form("u", "", "DSL2", null, false), objectMapper));
        expectThrows(
                BadRequestException.class,
                () -> WesRunService.checkForm(form("u", "NFL", null, null, false), objectMapper));
        BadRequestException attachment = expectThrows(
                BadRequestException.class,
                () -> WesRunService.checkForm(form("u", "NFL", "DSL2", null, true), objectMapper));
        assertTrue(attachment.getMessage().startsWith("workflow_attachment is not supported"));
        BadRequestException engineParameters = expectThrows(
                BadRequestException.class,
                () -> WesRunService.checkForm(form("u", "NFL", "DSL2", "{\"-resume\": \"\"}", false), objectMapper));
        assertTrue(engineParameters.getMessage().startsWith("workflow_engine_parameters are not supported"));
        expectThrows(
                BadRequestException.class,
                () -> WesRunService.checkForm(form("u", "NFL", "DSL2", "not json", false), objectMapper));
    }

    @Test
    public void requestMustDescribeTheAllowlistedWorkflowAndEngine() {
        WesRunService.checkEntry(entry(), engineForm(null, null), "nextflow", "26.10.5");
        WesRunService.checkEntry(entry(), engineForm("Nextflow", "26.10.5"), "nextflow", "26.10.5");

        BadRequestException wrongType = expectThrows(
                BadRequestException.class,
                () -> WesRunService.checkEntry(
                        entry(),
                        new WesRunService.RunRequestForm(
                                null, "CWL", "v1.2", null, null, null, null, "refcloud:bundled/drs-checksum", false),
                        "nextflow",
                        "26.10.5"));
        assertEquals(
                wrongType.getMessage(),
                "this workflow's workflow_type / workflow_type_version is NFL / DSL2, not CWL / v1.2");
        expectThrows(
                BadRequestException.class,
                () -> WesRunService.checkEntry(entry(), engineForm("cromwell", null), "nextflow", "26.10.5"));
        expectThrows(
                BadRequestException.class,
                () -> WesRunService.checkEntry(entry(), engineForm(null, "25.04.0"), "nextflow", "26.10.5"));
    }

    @Test
    public void paramsAndTagsMustBeJsonObjects() {
        assertTrue(
                WesRunService.parseObject("workflow_params", null, objectMapper).isEmpty());
        assertEquals(WesRunService.parseTags("{\"project\": \"demo\"}", objectMapper), Map.of("project", "demo"));

        expectThrows(
                BadRequestException.class, () -> WesRunService.parseObject("workflow_params", "[1]", objectMapper));
        expectThrows(BadRequestException.class, () -> WesRunService.parseTags("{\"n\": 1}", objectMapper));
    }

    @Test
    public void outputPathsResolveStrictlyInsideOutputs() throws IOException {
        Path outputs = Files.createDirectories(directory.resolve("outputs"));
        Files.createDirectories(outputs.resolve("report"));
        Files.writeString(outputs.resolve("report/summary.txt"), "ok");
        Files.writeString(directory.resolve("secret.txt"), "no");
        Files.createSymbolicLink(outputs.resolve("escape.txt"), directory.resolve("secret.txt"));
        Files.createSymbolicLink(outputs.resolve("inside.txt"), outputs.resolve("report/summary.txt"));

        assertEquals(
                WesRunService.resolveInside(outputs, "/report/summary.txt"),
                Optional.of(outputs.resolve("report/summary.txt").toRealPath()));
        assertEquals(
                WesRunService.resolveInside(outputs, "report/summary.txt"),
                Optional.of(outputs.resolve("report/summary.txt").toRealPath()));
        // a symlink that stays inside outputs is fine
        assertTrue(WesRunService.resolveInside(outputs, "inside.txt").isPresent());

        for (String rejected : new String[] {
            "../secret.txt",
            "/report/../../secret.txt",
            "report/./summary.txt",
            "//etc/passwd",
            "report//summary.txt",
            "escape.txt",
            "report",
            "missing.txt",
            "",
            "/",
            "report\\summary.txt"
        }) {
            assertEquals(WesRunService.resolveInside(outputs, rejected), Optional.empty(), rejected);
        }
    }

    @Test
    public void taskLogsNeverExposeTheExecutorsSystemLogs() {
        ComputeTask task = ComputeTask.builder()
                .id("t1")
                .name("CHECKSUM (1)")
                .request(objectMapper.readTree("{\"executors\": [{\"command\": [\"bash\", \"-c\", \"true\"]}]}"))
                .executorTask(objectMapper.readTree("""
                        {"logs": [{"system_logs": ["docker run -i --read-only /host/storage/runs/r/work:/work funnel-1"],
                                   "logs": [{"exit_code": 0}]}]}
                        """))
                .build();

        WesViews.TaskLog log = WesRunService.taskLog(task, "https://refcloud.ga4gh.localhost/ga4gh/tes/p/v1/tasks/");
        String json = objectMapper.writeValueAsString(log);

        assertEquals(log.exitCode(), Integer.valueOf(0));
        assertEquals(log.tesUri(), "https://refcloud.ga4gh.localhost/ga4gh/tes/p/v1/tasks/t1");
        assertFalse(json.contains("system_logs"), json);
        assertFalse(json.contains("docker run"), json);
    }

    @Test
    public void outputUrlsEncodeEachSegment() {
        assertEquals(WesRunService.encodePath("report/summary.txt"), "report/summary.txt");
        assertEquals(WesRunService.encodePath("my report/a+b?.txt"), "my%20report/a%2Bb%3F.txt");
    }
}
