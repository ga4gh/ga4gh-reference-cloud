package org.ga4gh.refcloud.api.compute.storage;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.springframework.core.io.Resource;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class S3RunStorageTest {

    private static final String RUN = "s3://refcloud-compute/ref/runs/r1";

    private static final String RUN_ROLE = "arn:aws:iam::123456789012:role/refcloud-compute-run";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private FakeS3Client s3;

    private FakeStsClient sts;

    private S3RunStorage storage;

    private static ComputeConfig.Storage settings(String prefix) {
        return new ComputeConfig.Storage(
                ComputeConfig.StorageType.S3,
                null,
                "refcloud-compute",
                prefix,
                "us-east-1",
                null,
                true,
                null,
                null,
                RUN_ROLE,
                null);
    }

    @BeforeMethod
    public void setUp() {
        s3 = new FakeS3Client();
        sts = new FakeStsClient();
        // 4-byte parts, so multipart uploads happen with small inputs
        storage = new S3RunStorage(settings("ref/"), () -> s3, () -> sts, 4);
    }

    private void put(String key, String content) {
        s3.objects.put("refcloud-compute/" + key, content.getBytes(StandardCharsets.UTF_8));
    }

    private static InputStream bytes(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void runsAreUnderThePrefixAndNothingIsWrittenUpFront() throws IOException {
        assertEquals(storage.allocate("r1"), RUN);
        assertEquals(
                new S3RunStorage(settings(""), () -> s3, () -> sts).allocate("r1"), "s3://refcloud-compute/runs/r1");
        assertTrue(s3.objects.isEmpty());
    }

    @Test
    public void workflowIsPublishedAsOneArchiveAndTheStagingDirectoryRemoved() throws IOException {
        Path staging = storage.workflowStaging(RUN);
        Files.writeString(staging.resolve("main.nf"), "workflow {}");

        storage.publishWorkflow(RUN, staging);

        byte[] archive = s3.objects.get("refcloud-compute/ref/runs/r1/engine/workflow.tar.gz");
        assertEquals(TarEntries.read(archive).keySet(), Set.of("main.nf"));
        assertFalse(Files.exists(staging));
    }

    @Test
    public void discardedStagingDirectoryIsRemoved() throws IOException {
        Path staging = storage.workflowStaging(RUN);
        Files.writeString(staging.resolve("main.nf"), "half");

        storage.discardWorkflowStaging(RUN, staging);

        assertFalse(Files.exists(staging));
        assertTrue(s3.objects.isEmpty());
    }

    @Test
    public void smallInputIsOnePutAndLargerOnesAreMultipart() throws IOException {
        assertEquals(
                storage.stageInput(RUN, "obj1/x.bai", bytes("abc"), 3L),
                "s3://refcloud-compute/ref/runs/r1/inputs/obj1/x.bai");
        assertEquals(s3.puts, 1);

        // 10 bytes in 4-byte parts: 4 + 4 + 2
        String location = storage.stageInput(RUN, "obj2/y.bam", bytes("0123456789"), 10L);

        assertEquals(location, "s3://refcloud-compute/ref/runs/r1/inputs/obj2/y.bam");
        assertEquals(
                new String(s3.objects.get("refcloud-compute/ref/runs/r1/inputs/obj2/y.bam"), StandardCharsets.UTF_8),
                "0123456789");
        assertEquals(s3.puts, 1);
        assertTrue(s3.uploads.isEmpty());
    }

    @Test
    public void inputOfTheWrongSizeIsNotKept() {
        IOException exception =
                expectThrows(IOException.class, () -> storage.stageInput(RUN, "obj1/x.bai", bytes("0123456789"), 3L));

        assertEquals(exception.getMessage(), "downloaded 10 bytes of x.bai, expected 3");
        assertTrue(s3.objects.isEmpty());
    }

    @Test
    public void failedMultipartUploadIsAborted() {
        InputStream failing = new InputStream() {
            private int sent;

            @Override
            public int read() throws IOException {
                if (sent++ < 6) {
                    return 'x';
                }
                throw new IOException("connection reset");
            }
        };

        expectThrows(IOException.class, () -> storage.stageInput(RUN, "obj1/x.bam", failing, null));

        assertEquals(s3.aborted, 1);
        assertTrue(s3.uploads.isEmpty());
        assertTrue(s3.objects.isEmpty());
    }

    @Test
    public void inputPathsMustStayUnderInputs() {
        for (String path : List.of("../x", "a/../../x", "/x", "a//x", "")) {
            expectThrows(IOException.class, () -> storage.stageInput(RUN, path, bytes("x"), null));
        }
        assertTrue(s3.objects.isEmpty());
    }

    // nextflow's empty "directory" objects aren't outputs; listing pages through (the fake lists 2 keys a page)
    @Test
    public void outputsAreTheRunsFilesUnderOutputs() throws IOException {
        put("ref/runs/r1/outputs/", "");
        put("ref/runs/r1/outputs/report.json", "{}");
        put("ref/runs/r1/outputs/qc/", "");
        put("ref/runs/r1/outputs/qc/summary.txt", "ok!");
        put("ref/runs/r1/work/ab/.exitcode", "0");
        put("ref/runs/r10/outputs/other.txt", "no");

        assertEquals(
                storage.listOutputs(RUN),
                List.of(new StoredFile("qc/summary.txt", 3), new StoredFile("report.json", 2)));
    }

    @Test
    public void outputsOpenByRelativePathOnly() throws IOException {
        put("ref/runs/r1/outputs/qc/summary.txt", "ok!");
        put("ref/runs/r2/outputs/secret.txt", "no");

        Resource summary = storage.openOutput(RUN, "/qc/summary.txt").orElseThrow();

        assertEquals(summary.contentLength(), 3);
        assertEquals(summary.getFilename(), "summary.txt");
        assertEquals(read(summary), "ok!");
        // each read is a new request, so Spring can read it more than once (e.g. for a range)
        assertEquals(read(summary), "ok!");
        assertEquals(storage.openOutput(RUN, "missing.txt"), Optional.empty());
        for (String path : List.of("../r2/outputs/secret.txt", "qc/../../r2/outputs/secret.txt", "qc//summary.txt")) {
            assertEquals(storage.openOutput(RUN, path), Optional.empty(), path);
        }
    }

    @Test
    public void engineLogsAreUnderEngine() throws IOException {
        put("ref/runs/r1/engine/engine.stdout.log", "hello");

        assertEquals(read(storage.openEngineLog(RUN, "engine.stdout.log").orElseThrow()), "hello");
        assertEquals(storage.openEngineLog(RUN, "engine.stderr.log"), Optional.empty());
    }

    @Test
    public void endedRunKeepsOnlyItsOutputsAndEngineFiles() {
        put("ref/runs/r1/engine/workflow.tar.gz", "tgz");
        put("ref/runs/r1/engine/nextflow.log", "log");
        put("ref/runs/r1/inputs/obj1/x.bai", "x");
        put("ref/runs/r1/work/ab/1/.command.sh", "a");
        put("ref/runs/r1/work/ab/2/.command.sh", "b");
        put("ref/runs/r1/work/cd/3/.exitcode", "0");
        put("ref/runs/r1/outputs/report.json", "{}");
        put("ref/runs/r10/work/keep", "other run");

        storage.removeIntermediates(RUN);

        assertEquals(
                s3.objects.keySet(),
                Set.of(
                        "refcloud-compute/ref/runs/r1/engine/nextflow.log",
                        "refcloud-compute/ref/runs/r1/outputs/report.json",
                        "refcloud-compute/ref/runs/r10/work/keep"));
    }

    // the probe also checks STS issues run credentials (for a probe "run" under health/), at the shortest lifetime
    @Test
    public void healthProbeLeavesNothingBehind() {
        assertEquals(storage.check(), Optional.empty());
        assertTrue(s3.objects.isEmpty());
        assertEquals(sts.requests.size(), 1);
        assertEquals(sts.requests.get(0).roleSessionName(), "refcloud-run-health");
        assertEquals(sts.requests.get(0).durationSeconds(), 900);
    }

    @Test
    public void refusedRunCredentialsAreUnhealthy() {
        sts.refuse = "not authorized to assume the run role";

        String failure = storage.check().orElseThrow();

        assertTrue(failure.contains("not authorized to assume the run role"), failure);
    }

    @Test
    public void runCredentialsAreLimitedToTheRunsPrefix() {
        RunCredentials credentials =
                storage.issueRunCredentials(RUN, Duration.ofMinutes(45)).orElseThrow();

        assertEquals(credentials.accessKeyId(), "ASIAFAKEKEYID");
        assertEquals(credentials.sessionToken(), "fake-session-token");
        assertEquals(sts.requests.size(), 1);
        var request = sts.requests.get(0);
        assertEquals(request.roleArn(), RUN_ROLE);
        assertEquals(request.roleSessionName(), "refcloud-run-r1");
        assertEquals(request.durationSeconds(), 45 * 60);
        assertEquals(objectMapper.readTree(request.policy()), objectMapper.readTree("""
                        {"Version": "2012-10-17", "Statement": [
                          {"Effect": "Allow",
                           "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:AbortMultipartUpload",
                                      "s3:ListMultipartUploadParts"],
                           "Resource": ["arn:aws:s3:::refcloud-compute/ref/runs/r1/*"]},
                          {"Effect": "Allow", "Action": ["s3:ListBucket"], "Resource": ["arn:aws:s3:::refcloud-compute"],
                           "Condition": {"StringLike": {"s3:prefix": ["ref/runs/r1", "ref/runs/r1/", "ref/runs/r1/*"]}}},
                          {"Effect": "Allow", "Action": ["s3:GetBucketLocation"],
                           "Resource": ["arn:aws:s3:::refcloud-compute"]}]}
                        """));
        // never in a log line
        assertFalse(credentials.toString().contains("fake-secret"));
        assertFalse(credentials.toString().contains("fake-session-token"));
    }

    @Test
    public void runCredentialLifetimeStaysWithinWhatStsIssues() {
        storage.issueRunCredentials(RUN, Duration.ofMinutes(5));
        assertEquals(sts.requests.get(0).durationSeconds(), 900);

        IllegalStateException tooLong = expectThrows(
                IllegalStateException.class, () -> storage.issueRunCredentials(RUN, Duration.ofSeconds(3601)));
        assertTrue(tooLong.getMessage().contains("at most 3600 seconds"), tooLong.getMessage());
    }

    @Test
    public void refusedRunCredentialsFailTheLaunchWithTheReason() {
        sts.refuse = "AccessDenied";

        IllegalStateException exception = expectThrows(
                IllegalStateException.class, () -> storage.issueRunCredentials(RUN, Duration.ofMinutes(30)));

        assertTrue(exception.getMessage().startsWith("could not issue the run's storage credentials"));
    }

    @Test
    public void unusableStorageIsUnhealthyWithTheReason() {
        S3RunStorage noCredentials = new S3RunStorage(
                settings("ref/"),
                () -> {
                    throw new IllegalStateException("S3 access key environment variable X is not set");
                },
                () -> sts);

        String failure = noCredentials.check().orElseThrow();

        assertTrue(failure.contains("s3://refcloud-compute/ref/"), failure);
        assertTrue(failure.contains("X is not set"), failure);
    }

    @Test
    public void runOnAnotherStorageIsRefused() {
        expectThrows(IllegalStateException.class, () -> storage.listOutputs("/storage/runs/r1"));
    }

    @Test
    public void inputsCanBeReadBackFromTheirLocation() throws IOException {
        String location = storage.stageInput(RUN, "obj1/x.bai", bytes("abc"), 3L);
        S3Location parsed = S3Location.parse(location).orElseThrow();

        assertEquals(parsed, new S3Location("refcloud-compute", "ref/runs/r1/inputs/obj1/x.bai"));
        assertEquals(Map.copyOf(s3.objects).size(), 1);
    }
}
