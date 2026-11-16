package org.ga4gh.refcloud.api.compute.wes;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.ga4gh.refcloud.api.compute.ComputeWesConfig;
import org.ga4gh.refcloud.api.compute.run.ComputeRun;
import org.ga4gh.refcloud.api.compute.run.ComputeRunService;
import org.ga4gh.refcloud.api.compute.run.ComputeRunStates;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class RunPreparationServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private Path directory;

    private Path base;

    private Path runDirectory;

    private ComputeRun run;

    private JsonNode preparedParams;

    private String failure;

    private RunPreparationService service;

    @BeforeMethod
    public void setUp() throws IOException {
        directory = Files.createTempDirectory("run-preparation-test");
        base = Files.createDirectories(directory.resolve("repo"));
        runDirectory = Files.createDirectories(directory.resolve("runs/r1"));
        preparedParams = null;
        failure = null;

        // only the calls preparation makes are needed; the rest of the run service isn't used
        ComputeRunService runs = new ComputeRunService(null, null, null, null, null, List.of()) {
            @Override
            public Optional<ComputeRun> findRun(String runId) {
                return Optional.ofNullable(run);
            }

            @Override
            public boolean markPrepared(String runId, JsonNode workflowParams) {
                preparedParams = workflowParams;
                return true;
            }

            @Override
            public void failPreparation(String runId, String reason) {
                failure = reason;
            }
        };
        ComputeWesConfig config = new ComputeWesConfig(
                base.toString(), Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(1), 256, 2048);
        service = new RunPreparationService(runs, config, Runnable::run);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private ComputeRun queuedRun(String source, String drsInputs, String params) {
        return ComputeRun.builder()
                .id("r1")
                .state(ComputeRunStates.QUEUED)
                .runDirectory(runDirectory.toString())
                .workflowSource(objectMapper.readTree(source))
                .drsInputs(objectMapper.readTree(drsInputs))
                .workflowParams(objectMapper.readTree(params))
                .build();
    }

    @Test
    public void bundledWorkflowIsCopiedWithoutSymlinks() throws IOException {
        Path workflow = Files.createDirectories(base.resolve("compute/workflows/nextflow/demo/conf"));
        Files.writeString(workflow.getParent().resolve("main.nf"), "workflow {}");
        Files.writeString(workflow.resolve("base.config"), "params {}");
        Files.writeString(directory.resolve("outside.txt"), "secret");
        Files.createSymbolicLink(workflow.resolve("link.txt"), directory.resolve("outside.txt"));
        run = queuedRun(
                "{\"kind\": \"bundled\", \"bundled_path\": \"compute/workflows/nextflow/demo\"}", "[]", "{\"n\": 1}");

        service.prepareInBackground("r1");

        assertNull(failure);
        assertEquals(preparedParams, objectMapper.readTree("{\"n\": 1}"));
        assertEquals(Files.readString(runDirectory.resolve("workflow/main.nf")), "workflow {}");
        assertTrue(Files.exists(runDirectory.resolve("workflow/conf/base.config")));
        assertFalse(Files.exists(runDirectory.resolve("workflow/conf/link.txt")));
    }

    @Test
    public void bundledPathMustStayInsideTheBaseDirectory() throws IOException {
        Files.createDirectories(directory.resolve("elsewhere"));
        run = queuedRun("{\"kind\": \"bundled\", \"bundled_path\": \"../elsewhere\"}", "[]", "{}");

        service.prepareInBackground("r1");

        assertNull(preparedParams);
        assertEquals(failure, "the run could not be prepared: bundled workflow not found: ../elsewhere");
    }

    @Test
    public void gitSourceMustBeAGithubRepositoryAndFullSha() {
        run = queuedRun(
                "{\"kind\": \"git\", \"repository\": \"https://gitlab.com/a/b\", \"commit\": \"abc\"}", "[]", "{}");

        service.prepareInBackground("r1");

        assertNull(preparedParams);
        assertTrue(failure.contains("the allowlisted git source is invalid"), failure);
    }

    @Test
    public void inputsAreOnlyDownloadedOverHttps() throws IOException {
        Files.createDirectories(base.resolve("compute/workflows/nextflow/demo"));
        run = queuedRun(
                "{\"kind\": \"bundled\", \"bundled_path\": \"compute/workflows/nextflow/demo\"}",
                """
                [{"uri": "drs://h/x", "object_id": "x", "name": "x.bai", "size": 1, "url": "http://bucket/x.bai"}]
                """,
                "{\"input\": \"drs://h/x\"}");

        service.prepareInBackground("r1");

        assertNull(preparedParams);
        assertEquals(failure, "the run could not be prepared: refusing to download a non-https input url");
    }

    @Test
    public void runsThatAreNoLongerWaitingAreLeftAlone() {
        run = queuedRun("{\"kind\": \"bundled\", \"bundled_path\": \"missing\"}", "[]", "{}");
        run.setState(ComputeRunStates.CANCELED);

        service.prepareInBackground("r1");

        assertNull(preparedParams);
        assertNull(failure);
    }
}
