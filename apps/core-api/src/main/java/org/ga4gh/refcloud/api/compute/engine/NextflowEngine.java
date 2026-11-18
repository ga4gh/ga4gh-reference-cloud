package org.ga4gh.refcloud.api.compute.engine;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputePlatformType;
import org.ga4gh.refcloud.api.compute.ComputeRunConfig;
import org.ga4gh.refcloud.api.compute.run.ComputeRun;
import org.ga4gh.refcloud.api.compute.storage.RunCredentials;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// runs a workflow with Nextflow, sending every task to the refcloud TES through the nf-ga4gh plugin (D3, D12; the
// details were settled in the Task 1 spike):
//   NXF_VER pins the nextflow version (Nextflow's launcher honours it; a self-contained build such as Homebrew's
//   doesn't, and the health check reports that connection unhealthy)
//   NXF_PLUGINS_DEFAULT adds nf-ga4gh without replacing the pipeline's own plugins (a -c plugins block would)
//   refcloud.config selects the tes executor, our TES endpoint, and the run token, read from the environment
//   -params-file passes the researcher's workflow_params; --outdir always points at the run's outputs/ directory
@Slf4j
@Component
public class NextflowEngine implements WorkflowEngine {

    static final String CONFIG_FILE = "refcloud.config";

    static final String PARAMS_FILE = "params.json";

    static final String LOG_FILE = "nextflow.log";

    static final String TOKEN_ENVIRONMENT_VARIABLE = "REFCLOUD_TES_TOKEN";

    static final String ABORT_MARKER = "Session aborted -- Cause:";

    // how much of the end of nextflow.log is read when looking for why a run failed
    static final long LOG_TAIL_BYTES = 256 * 1024;

    private final HostProcessRunner hostProcessRunner;

    private final ComputeRunConfig runConfig;

    private final ObjectMapper objectMapper;

    public NextflowEngine(HostProcessRunner hostProcessRunner, ComputeRunConfig runConfig, ObjectMapper objectMapper) {
        this.hostProcessRunner = hostProcessRunner;
        this.runConfig = runConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public ComputePlatformType platformType() {
        return ComputePlatformType.NEXTFLOW_FUNNEL_DOCKER;
    }

    @Override
    public Set<String> reservedEnvironment() {
        return Set.of("NXF_VER", "NXF_PLUGINS_DEFAULT", "NXF_HOME", "REFCLOUD_");
    }

    // runs on a shared filesystem only, so there are never storage credentials
    @Override
    public EngineProcess launch(
            ComputeRun run,
            ComputeConfig.Connection connection,
            String tesEndpoint,
            String tesToken,
            Optional<RunCredentials> storageCredentials)
            throws IOException {
        Path runDirectory = Path.of(run.getRunDirectory());
        writeRunFiles(run, tesEndpoint, objectMapper);
        return hostProcessRunner.launch(
                runDirectory,
                command(run, connection.engine()),
                environment(run, connection.engine(), tesToken),
                runConfig.launchTimeout());
    }

    @Override
    public EngineStatus status(ComputeRun run, ComputeConfig.Connection connection) {
        return hostProcessRunner.status(Path.of(run.getRunDirectory()), process(run));
    }

    @Override
    public void terminate(ComputeRun run, ComputeConfig.Connection connection, boolean force) {
        hostProcessRunner.signal(Path.of(run.getRunDirectory()), process(run), force);
    }

    // a host process leaves nothing of its own behind; the run's storage is cleaned up by the run service
    @Override
    public void cleanUp(ComputeRun run, ComputeConfig.Connection connection) {}

    // the run's generated nextflow config and its params file, in the run directory (shared with
    // NextflowKubernetesEngine, as are the command, the environment and refcloudFailureOf)
    static void writeRunFiles(ComputeRun run, String tesEndpoint, ObjectMapper objectMapper) throws IOException {
        Path runDirectory = Path.of(run.getRunDirectory());
        Files.writeString(runDirectory.resolve(CONFIG_FILE), config(tesEndpoint), StandardCharsets.UTF_8);
        Files.writeString(
                runDirectory.resolve(PARAMS_FILE),
                objectMapper.writeValueAsString(run.getWorkflowParams()),
                StandardCharsets.UTF_8);
    }

    // nextflow logs why it aborted a workflow as "Session aborted -- Cause: <message>". when the message is a failed
    // request to this platform's refcloud TES (/ga4gh/tes/<platformId>/v1/...; the host isn't compared, in case the
    // callback url changed), the run failed because the engine lost contact with the refcloud, not because the workflow
    // failed. only the end of the run's own log is read
    @Override
    public Optional<String> refcloudFailure(ComputeRun run) {
        return refcloudFailureOf(run);
    }

    static Optional<String> refcloudFailureOf(ComputeRun run) {
        Path log = Path.of(run.getRunDirectory()).resolve(LOG_FILE);
        return refcloudFailureIn(run, tail(log, LOG_TAIL_BYTES));
    }

    // the same, from the end of the log's text (NextflowKubernetesEngine reads it through the run's storage)
    static Optional<String> refcloudFailureIn(ComputeRun run, String logTail) {
        return abortCause(logTail)
                .filter(cause -> cause.contains("/ga4gh/tes/" + run.getComputePlatformId() + "/v1/"))
                .map(cause -> "the workflow engine lost contact with the refcloud TES: " + truncate(cause, 300));
    }

    // the last "Session aborted -- Cause: ..." message in the log text
    static Optional<String> abortCause(String logText) {
        int at = logText.lastIndexOf(ABORT_MARKER);
        if (at < 0) {
            return Optional.empty();
        }
        int start = at + ABORT_MARKER.length();
        int end = logText.indexOf('\n', start);
        return Optional.of((end < 0 ? logText.substring(start) : logText.substring(start, end)).strip());
    }

    private static String tail(Path file, long bytes) {
        try (RandomAccessFile reader = new RandomAccessFile(file.toFile(), "r")) {
            long start = Math.max(0, reader.length() - bytes);
            byte[] buffer = new byte[(int) (reader.length() - start)];
            reader.seek(start);
            reader.readFully(buffer);
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }

    private static String truncate(String text, int length) {
        return text.length() <= length ? text : text.substring(0, length) + "...";
    }

    // the generated config: our executor settings, with the token read from the environment so it's never on disk
    static String config(String tesEndpoint) {
        // a groovy single-quoted string: escape backslashes, then quotes
        String endpoint = tesEndpoint.replace("\\", "\\\\").replace("'", "\\'");
        return String.join(
                "\n",
                "// written by core-api for this run (claude/specs/archive/0008-add-compute-wes-tes.md, Task 7); do not edit",
                "process.executor = 'tes'",
                "tes.endpoint = '" + endpoint + "'",
                "tes.oauthToken = System.getenv('" + TOKEN_ENVIRONMENT_VARIABLE + "')",
                "");
    }

    static List<String> command(ComputeRun run, ComputeConfig.Engine engine) {
        return command(run, engine.nextflowExecutable());
    }

    // where the engine finds the run's files: its local directory (its launch directory, with the workflow, config,
    // params and log) and the workflow in it, and where its work directory and outputs go. all in the run directory
    // on a shared filesystem; on S3 the local directory is the Job's own and the rest are s3:// prefixes (spec 0012)
    record RunFiles(String directory, String workflow, String workDir, String outputs) {

        static RunFiles shared(ComputeRun run) {
            Path runDirectory = Path.of(run.getRunDirectory());
            return new RunFiles(
                    runDirectory.toString(),
                    run.getWorkflowPath(),
                    runDirectory.resolve("work").toString(),
                    runDirectory.resolve("outputs").toString());
        }
    }

    // the nextflow command for a run on a shared filesystem, run by executable
    static List<String> command(ComputeRun run, String executable) {
        return command(run, RunFiles.shared(run), executable);
    }

    static List<String> command(ComputeRun run, RunFiles files, String executable) {
        Path directory = Path.of(files.directory());
        List<String> command = new ArrayList<>(List.of(
                executable,
                "-log",
                directory.resolve(LOG_FILE).toString(),
                "run",
                files.workflow(),
                "-ansi-log",
                "false",
                "-c",
                directory.resolve(CONFIG_FILE).toString(),
                "-work-dir",
                files.workDir(),
                "-params-file",
                directory.resolve(PARAMS_FILE).toString()));
        if (run.getMainScript() != null && !run.getMainScript().equals("main.nf")) {
            command.add("-main-script");
            command.add(run.getMainScript());
        }
        if (run.getEngineProfiles() != null && !run.getEngineProfiles().isEmpty()) {
            command.add("-profile");
            command.add(String.join(",", run.getEngineProfiles()));
        }
        // last, so it overrides any outdir in the params file
        command.add("--outdir");
        command.add(files.outputs());
        return command;
    }

    static Map<String, String> environment(ComputeRun run, ComputeConfig.Engine engine, String tesToken) {
        Map<String, String> environment = pinnedEnvironment(run, engine);
        environment.put(TOKEN_ENVIRONMENT_VARIABLE, tesToken);
        return environment;
    }

    // the allowlist entry's variables, then the pinned versions, which always win (every engine type; the token is
    // added separately)
    static Map<String, String> pinnedEnvironment(ComputeRun run, ComputeConfig.Engine engine) {
        Map<String, String> environment = new HashMap<>();
        if (run.getEngineEnv() != null) {
            environment.putAll(run.getEngineEnv());
        }
        environment.put("NXF_VER", engine.nextflowVersion());
        environment.put("NXF_PLUGINS_DEFAULT", "nf-ga4gh@" + engine.nfGa4ghVersion());
        environment.put("NXF_ANSI_LOG", "false");
        return environment;
    }

    private static EngineProcess process(ComputeRun run) {
        return EngineProcess.hostProcess(run.getEnginePid(), run.getEngineStartedAt());
    }
}
