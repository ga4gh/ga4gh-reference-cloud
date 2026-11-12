package org.ga4gh.refcloud.api.compute.engine;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

// runs a workflow engine as a host process for one run, tracked through files in the run directory rather than an
// in-memory process handle, so core-api can reattach to it after a restart (Task 7). a small launcher script starts the
// engine, writes its pid to engine.pid, and its exit code to engine.exitcode when it finishes
@Component
public class HostProcessRunner {

    static final String LAUNCHER = ".refcloud-launcher.sh";

    static final String PID_FILE = "engine.pid";

    static final String EXIT_CODE_FILE = "engine.exitcode";

    // a process gone with no exit code yet may be the launcher still writing it; wait this long before calling it lost
    private static final Duration EXIT_CODE_GRACE = Duration.ofSeconds(3);

    // starts the command in the run directory with these extra environment variables, and returns once the engine's
    // pid is recorded (or fails after launchTimeout)
    @SuppressFBWarnings(
            value = "COMMAND_INJECTION",
            justification = "the command comes from the deployer's connections file and paths core-api created; it is"
                    + " passed as an argument list (the launcher runs it as \"$@\"), never interpolated by a shell")
    public EngineProcess launch(
            Path runDirectory, List<String> command, Map<String, String> environment, Duration launchTimeout)
            throws IOException {
        Path launcher = runDirectory.resolve(LAUNCHER);
        try (InputStream script = HostProcessRunner.class.getResourceAsStream("/compute/run-launcher.sh")) {
            if (script == null) {
                throw new IOException("the run launcher script is missing from the classpath");
            }
            Files.write(launcher, script.readAllBytes());
        }

        List<String> launch = new ArrayList<>(List.of("sh", launcher.toString(), runDirectory.toString()));
        launch.addAll(command);
        ProcessBuilder builder = new ProcessBuilder(launch)
                .directory(runDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(
                        runDirectory.resolve("launcher.log").toFile()));
        builder.environment().putAll(environment);
        Process launcherProcess = builder.start();

        Instant deadline = Instant.now().plus(launchTimeout);
        Path pidFile = runDirectory.resolve(PID_FILE);
        while (Instant.now().isBefore(deadline)) {
            Optional<Long> pid = readLong(pidFile);
            if (pid.isPresent()) {
                Instant startedAt = ProcessHandle.of(pid.get())
                        .flatMap(handle -> handle.info().startInstant())
                        .orElse(null);
                return new EngineProcess(pid.get(), startedAt);
            }
            if (!launcherProcess.isAlive() && readLong(pidFile).isEmpty()) {
                throw new IOException("the run launcher exited (" + launcherProcess.exitValue()
                        + ") before starting the engine; see launcher.log");
            }
            sleep(Duration.ofMillis(100));
        }
        launcherProcess.destroyForcibly();
        throw new IOException("the engine did not start within " + launchTimeout);
    }

    public EngineStatus status(Path runDirectory, EngineProcess process) {
        Optional<Integer> exitCode = exitCode(runDirectory);
        if (exitCode.isPresent()) {
            return new EngineStatus.Exited(exitCode.get());
        }
        if (find(runDirectory, process).isPresent()) {
            return new EngineStatus.Alive();
        }
        // the engine just exited and the launcher may still be writing its exit code
        Instant deadline = Instant.now().plus(EXIT_CODE_GRACE);
        while (Instant.now().isBefore(deadline)) {
            exitCode = exitCode(runDirectory);
            if (exitCode.isPresent()) {
                return new EngineStatus.Exited(exitCode.get());
            }
            sleep(Duration.ofMillis(200));
        }
        return new EngineStatus.Lost();
    }

    // SIGTERM (force: SIGKILL), only if the pid is still the run's engine
    public void signal(Path runDirectory, EngineProcess process, boolean force) {
        find(runDirectory, process).ifPresent(handle -> {
            if (force) {
                handle.destroyForcibly();
            } else {
                handle.destroy();
            }
        });
    }

    // the engine's process, if the pid still belongs to it. the start time is the identity (a pid reused by another
    // process has a different start time); only when the OS doesn't report a start time is the command line used
    Optional<ProcessHandle> find(Path runDirectory, EngineProcess process) {
        return ProcessHandle.of(process.pid())
                .filter(ProcessHandle::isAlive)
                .filter(handle -> isSameProcess(handle.info(), runDirectory, process));
    }

    static boolean isSameProcess(ProcessHandle.Info info, Path runDirectory, EngineProcess process) {
        Optional<Instant> startInstant = info.startInstant();
        if (process.startedAt() != null && startInstant.isPresent()) {
            return Math.abs(Duration.between(process.startedAt(), startInstant.get())
                            .toMillis())
                    <= 1000;
        }
        return info.commandLine()
                .map(commandLine -> commandLine.contains(runDirectory.toString()))
                .orElse(true);
    }

    private static Optional<Integer> exitCode(Path runDirectory) {
        return readLong(runDirectory.resolve(EXIT_CODE_FILE)).map(Long::intValue);
    }

    private static Optional<Long> readLong(Path file) {
        try {
            return Optional.of(Long.parseLong(
                    Files.readString(file, StandardCharsets.UTF_8).strip()));
        } catch (IOException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
