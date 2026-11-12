package org.ga4gh.refcloud.api.compute.engine;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.run.ComputeRun;
import org.testng.annotations.Test;

public class NextflowEngineTest {

    private static final ComputeConfig.Engine ENGINE =
            new ComputeConfig.Engine("/usr/local/bin/nextflow", "26.10.5", "1.5.0");

    private static ComputeRun run(List<String> profiles, Map<String, String> engineEnv) {
        return ComputeRun.builder()
                .id("run-1")
                .runDirectory("/storage/runs/run-1")
                .workflowPath("/storage/runs/run-1/workflow")
                .engineProfiles(profiles)
                .engineEnv(engineEnv)
                .build();
    }

    @Test
    public void configSendsTasksToTheRefcloudTesWithTheTokenFromTheEnvironment() {
        String config = NextflowEngine.config("http://localhost:8080/ga4gh/tes/demo");

        assertTrue(config.contains("process.executor = 'tes'"), config);
        assertTrue(config.contains("tes.endpoint = 'http://localhost:8080/ga4gh/tes/demo'"), config);
        assertTrue(config.contains("tes.oauthToken = System.getenv('REFCLOUD_TES_TOKEN')"), config);
        assertFalse(config.contains("rcrun_"), "the token itself never goes in the file");
    }

    @Test
    public void configEscapesTheEndpointForGroovy() {
        assertTrue(NextflowEngine.config("http://h/it's\\x").contains("tes.endpoint = 'http://h/it\\'s\\\\x'"));
    }

    @Test
    public void commandRunsTheWorkflowInItsRunDirectory() {
        List<String> command = NextflowEngine.command(run(List.of("test", "docker"), Map.of()), ENGINE);

        assertEquals(
                command,
                List.of(
                        "/usr/local/bin/nextflow",
                        "-log",
                        "/storage/runs/run-1/nextflow.log",
                        "run",
                        "/storage/runs/run-1/workflow",
                        "-ansi-log",
                        "false",
                        "-c",
                        "/storage/runs/run-1/refcloud.config",
                        "-work-dir",
                        "/storage/runs/run-1/work",
                        "-params-file",
                        "/storage/runs/run-1/params.json",
                        "-profile",
                        "test,docker",
                        "--outdir",
                        "/storage/runs/run-1/outputs"));
    }

    @Test
    public void noProfilesMeansNoProfileOption() {
        List<String> command = NextflowEngine.command(run(List.of(), Map.of()), ENGINE);

        assertFalse(command.contains("-profile"));
        // --outdir always last, so it wins over the params file
        assertEquals(command.get(command.size() - 2), "--outdir");
    }

    @Test
    public void environmentPinsTheVersionAndPluginAndCarriesTheToken() {
        Map<String, String> environment = NextflowEngine.environment(
                run(List.of(), Map.of("NXF_SYNTAX_PARSER", "v1", "NXF_VER", "1.0.0")), ENGINE, "rcrun_secret");

        assertEquals(environment.get("NXF_SYNTAX_PARSER"), "v1");
        // the refcloud's values always win over an allowlist entry's
        assertEquals(environment.get("NXF_VER"), "26.10.5");
        assertEquals(environment.get("NXF_PLUGINS_DEFAULT"), "nf-ga4gh@1.5.0");
        assertEquals(environment.get("REFCLOUD_TES_TOKEN"), "rcrun_secret");
    }

    @Test
    public void reservedEnvironment() {
        NextflowEngine engine = new NextflowEngine(null, null, null);

        for (String reserved :
                List.of("NXF_VER", "NXF_PLUGINS_DEFAULT", "NXF_HOME", "REFCLOUD_TES_TOKEN", "REFCLOUD_X")) {
            assertTrue(engine.isReservedEnvironmentVariable(reserved), reserved);
        }
        for (String allowed : List.of("NXF_SYNTAX_PARSER", "NXF_VERSION_CHECK", "JAVA_OPTS")) {
            assertFalse(engine.isReservedEnvironmentVariable(allowed), allowed);
        }
    }

    private static ComputeRun runWithLog(String platformId, String fixture) throws Exception {
        Path runDirectory = Files.createTempDirectory("nextflow-engine-test");
        if (fixture != null) {
            try (InputStream log = NextflowEngineTest.class.getResourceAsStream("/compute/" + fixture)) {
                Files.write(runDirectory.resolve("nextflow.log"), log.readAllBytes());
            }
        }
        return ComputeRun.builder()
                .id("run-1")
                .computePlatformId(platformId)
                .runDirectory(runDirectory.toString())
                .build();
    }

    @Test
    public void lostContactWithTheRefcloudTesIsARefcloudFailure() throws Exception {
        // the real log of a run whose nextflow aborted while core-api was down (Task 7 restart test)
        Optional<String> reason = new NextflowEngine(null, null, null)
                .refcloudFailure(runWithLog("nf-demo", "nextflow-lost-refcloud-tes.log"));

        assertTrue(
                reason.orElseThrow().startsWith("the workflow engine lost contact with the refcloud TES: "),
                reason.toString());
        assertTrue(reason.get().contains("ConnectException"), reason.toString());
    }

    @Test
    public void anotherPlatformsEndpointIsNotThisRunsRefcloudFailure() throws Exception {
        assertFalse(new NextflowEngine(null, null, null)
                .refcloudFailure(runWithLog("other-platform", "nextflow-lost-refcloud-tes.log"))
                .isPresent());
    }

    @Test
    public void failedWorkflowIsNotARefcloudFailure() throws Exception {
        assertFalse(new NextflowEngine(null, null, null)
                .refcloudFailure(runWithLog("nf-demo", "nextflow-workflow-failed.log"))
                .isPresent());
    }

    @Test
    public void missingLogIsNotARefcloudFailure() throws Exception {
        assertFalse(new NextflowEngine(null, null, null)
                .refcloudFailure(runWithLog("nf-demo", null))
                .isPresent());
    }

    @Test
    public void lastAbortCauseWins() {
        String log = "a\nx Session aborted -- Cause: first\ny\nz Session aborted -- Cause: second one \nend\n";

        assertEquals(NextflowEngine.abortCause(log), Optional.of("second one"));
        assertEquals(NextflowEngine.abortCause("no abort here"), Optional.empty());
    }
}
