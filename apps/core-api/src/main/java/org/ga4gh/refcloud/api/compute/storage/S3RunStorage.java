package org.ga4gh.refcloud.api.compute.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.utils.SdkAutoCloseable;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

// runs in an S3 bucket (spec 0012): each run is <prefix>runs/<run_id>/ in the connection's bucket, and nothing is kept
// on core-api's own disk. the engine and executor reach the same objects with their own credentials: Funnel's workers
// have the connection's, and each run's engine gets temporary ones from STS, limited to the run's prefix (Task 3).
// files
// the engine itself needs locally (the workflow, its config) are fetched by the engine; the workflow goes up as one
// archive, engine/workflow.tar.gz, so that's a single download
@Slf4j
public class S3RunStorage implements RunStorage {

    static final String WORKFLOW_ARCHIVE = "engine/workflow.tar.gz";

    // inputs are uploaded in parts of this size (in memory, one per input being staged), so a download of any size
    // streams straight into the bucket: a smaller input is a single PUT. 10,000 parts (S3's limit) is 160 GB
    static final int PART_SIZE = 16 * 1024 * 1024;

    // S3 deletes at most this many objects per request
    static final int DELETE_BATCH = 1000;

    private final ComputeConfig.Storage settings;

    private final Supplier<S3Client> clients;

    private final Supplier<StsClient> stsClients;

    private final int partSize;

    private volatile S3Client client;

    private volatile StsClient stsClient;

    // clients and stsClients build the connection's S3 and STS clients on first use (RunStorages), so a missing
    // credential shows up as an unhealthy connection rather than a failed startup
    public S3RunStorage(ComputeConfig.Storage settings, Supplier<S3Client> clients, Supplier<StsClient> stsClients) {
        this(settings, clients, stsClients, PART_SIZE);
    }

    // tests use small parts
    S3RunStorage(
            ComputeConfig.Storage settings, Supplier<S3Client> clients, Supplier<StsClient> stsClients, int partSize) {
        this.settings = settings;
        this.clients = clients;
        this.stsClients = stsClients;
        this.partSize = partSize;
    }

    @Override
    public ComputeConfig.StorageType type() {
        return ComputeConfig.StorageType.S3;
    }

    @Override
    public String allocate(String runId) {
        return new S3Location(settings.bucket(), settings.prefix() + "runs/" + runId).toString();
    }

    @Override
    public Path workflowStaging(String location) throws IOException {
        run(location);
        return Files.createTempDirectory("refcloud-workflow-");
    }

    @Override
    public void publishWorkflow(String location, Path staging) throws IOException {
        S3Location archive = run(location).child(WORKFLOW_ARCHIVE);
        Path file = Files.createTempFile("refcloud-workflow-", ".tar.gz");
        try {
            try (OutputStream out = Files.newOutputStream(file)) {
                WorkflowArchive.write(staging, out);
            }
            client().putObject(
                            PutObjectRequest.builder()
                                    .bucket(archive.bucket())
                                    .key(archive.key())
                                    .contentType("application/gzip")
                                    .build(),
                            RequestBody.fromFile(file));
        } finally {
            Files.deleteIfExists(file);
            SharedFilesystemRunStorage.deleteRecursively(staging);
        }
    }

    @Override
    public void discardWorkflowStaging(String location, Path staging) {
        try {
            SharedFilesystemRunStorage.deleteRecursively(staging);
        } catch (IOException exception) {
            log.warn("could not remove workflow staging directory {}: {}", staging, exception.toString());
        }
    }

    @Override
    public String stageInput(String location, String relativePath, InputStream content, Long expectedSize)
            throws IOException {
        if (!RelativePaths.isSafe(relativePath)) {
            throw new IOException("invalid input path: " + relativePath);
        }
        S3Location target = run(location).child("inputs/" + relativePath);
        long size = upload(target, content);
        if (expectedSize != null && size != expectedSize) {
            delete(target);
            String name = relativePath.substring(relativePath.lastIndexOf('/') + 1);
            throw new IOException("downloaded " + size + " bytes of " + name + ", expected " + expectedSize);
        }
        return target.toString();
    }

    // Nextflow writes empty "directory" objects (keys ending in "/"); those aren't files
    @Override
    public List<StoredFile> listOutputs(String location) {
        S3Location outputs = run(location).child("outputs/");
        List<StoredFile> files = new ArrayList<>();
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(outputs.bucket())
                .prefix(outputs.key())
                .build();
        for (S3Object object : client().listObjectsV2Paginator(request).contents()) {
            if (!object.key().endsWith("/")) {
                files.add(new StoredFile(object.key().substring(outputs.key().length()), object.size()));
            }
        }
        files.sort(Comparator.comparing(StoredFile::relativePath));
        return files;
    }

    @Override
    public Optional<Resource> openOutput(String location, String relativePath) {
        String path = RelativePaths.stripLeadingSlash(relativePath);
        if (!RelativePaths.isSafe(path)) {
            return Optional.empty();
        }
        return open(run(location).child("outputs/" + path));
    }

    @Override
    public Optional<Resource> openEngineLog(String location, String name) {
        if (!RelativePaths.isSafe(name) || name.contains("/")) {
            return Optional.empty();
        }
        return open(run(location).child("engine/" + name));
    }

    @Override
    public void writeEngineFile(String location, String name, String content) {
        S3Location file = engineFile(location, name);
        client().putObject(
                        PutObjectRequest.builder()
                                .bucket(file.bucket())
                                .key(file.key())
                                .build(),
                        RequestBody.fromString(content, StandardCharsets.UTF_8));
    }

    @Override
    public Optional<String> readEngineFile(String location, String name) {
        S3Location file = engineFile(location, name);
        try {
            return Optional.of(client().getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(file.bucket())
                            .key(file.key())
                            .build())
                    .asUtf8String());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    // a suffix range ("bytes=-N"): the object's last N bytes, or all of it if it's shorter
    @Override
    public String readEngineFileTail(String location, String name, long bytes) {
        S3Location file = engineFile(location, name);
        try {
            return client().getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(file.bucket())
                            .key(file.key())
                            .range("bytes=-" + bytes)
                            .build())
                    .asUtf8String();
        } catch (S3Exception exception) {
            // 416: the object is empty
            if (exception.statusCode() == 404 || exception.statusCode() == 416) {
                return "";
            }
            throw exception;
        }
    }

    private static S3Location engineFile(String location, String name) {
        if (!RelativePaths.isSafe(name) || name.contains("/")) {
            throw new IllegalArgumentException("invalid engine file name: " + name);
        }
        return run(location).child("engine/" + name);
    }

    // STS AssumeRole of the connection's run role, with a session policy that allows only the run's own prefix: what
    // the engine can do is what both the role (or, on RustFS, core-api's own user) and this policy allow
    @Override
    public Optional<RunCredentials> issueRunCredentials(String location, Duration lifetime) {
        S3Location run = run(location);
        long seconds = Math.max(lifetime.toSeconds(), ComputeConfig.Storage.MIN_CREDENTIAL_SECONDS);
        if (seconds > settings.runCredentialsMaxSeconds()) {
            throw new IllegalStateException("a run's storage credentials can last at most "
                    + settings.runCredentialsMaxSeconds() + " seconds, not " + seconds);
        }
        try {
            Credentials credentials = sts().assumeRole(AssumeRoleRequest.builder()
                            .roleArn(settings.runRoleArn())
                            .roleSessionName(sessionName(run))
                            .durationSeconds((int) seconds)
                            .policy(sessionPolicy(run))
                            .build())
                    .credentials();
            return Optional.of(new RunCredentials(
                    credentials.accessKeyId(),
                    credentials.secretAccessKey(),
                    credentials.sessionToken(),
                    credentials.expiration()));
        } catch (SdkException exception) {
            throw new IllegalStateException(
                    "could not issue the run's storage credentials: " + exception.getMessage(), exception);
        }
    }

    // the run's objects (read, write, delete, multipart uploads) and listing its keys; nothing else in the bucket.
    // GetBucketLocation lets an S3 client find the bucket's region (it reveals nothing about the objects)
    static String sessionPolicy(S3Location run) {
        JsonNodeFactory json = JsonNodeFactory.instance;
        String bucket = "arn:aws:s3:::" + run.bucket();
        ObjectNode policy = json.objectNode();
        policy.put("Version", "2012-10-17");
        ArrayNode statements = policy.putArray("Statement");
        ObjectNode objects = statements.addObject();
        objects.put("Effect", "Allow");
        objects.putArray("Action")
                .add("s3:GetObject")
                .add("s3:PutObject")
                .add("s3:DeleteObject")
                .add("s3:AbortMultipartUpload")
                .add("s3:ListMultipartUploadParts");
        objects.putArray("Resource").add(bucket + "/" + run.key() + "/*");
        ObjectNode listing = statements.addObject();
        listing.put("Effect", "Allow");
        listing.putArray("Action").add("s3:ListBucket");
        listing.putArray("Resource").add(bucket);
        listing.putObject("Condition")
                .putObject("StringLike")
                .putArray("s3:prefix")
                .add(run.key())
                .add(run.key() + "/")
                .add(run.key() + "/*");
        ObjectNode location = statements.addObject();
        location.put("Effect", "Allow");
        location.putArray("Action").add("s3:GetBucketLocation");
        location.putArray("Resource").add(bucket);
        return policy.toString();
    }

    // e.g. refcloud-run-<run id>: shows in the server's (or CloudTrail's) records of who did what
    static String sessionName(S3Location run) {
        String key = run.key();
        return "refcloud-run-" + key.substring(key.lastIndexOf('/') + 1);
    }

    @Override
    public void removeIntermediates(String location) {
        S3Location run = run(location);
        try {
            deletePrefix(run.child("inputs/"));
            deletePrefix(run.child("work/"));
            delete(run.child(WORKFLOW_ARCHIVE));
        } catch (SdkException exception) {
            log.warn("could not fully remove the intermediate files of {}: {}", location, exception.toString());
        }
    }

    // a probe object can be written, read and deleted under the connection's prefix, and STS issues run credentials
    // (for a probe "run" under the prefix's health/, at the shortest lifetime)
    @Override
    public Optional<String> check() {
        S3Location probe = new S3Location(settings.bucket(), settings.prefix() + "health/probe-" + UUID.randomUUID());
        try {
            S3Client s3 = client();
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(probe.bucket())
                            .key(probe.key())
                            .build(),
                    RequestBody.fromString("ok"));
            s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(probe.bucket())
                    .key(probe.key())
                    .build());
            delete(probe);
            issueRunCredentials(
                    new S3Location(settings.bucket(), settings.prefix() + "health").toString(),
                    Duration.ofSeconds(ComputeConfig.Storage.MIN_CREDENTIAL_SECONDS));
            return Optional.empty();
        } catch (SdkException | IllegalStateException exception) {
            return Optional.of("S3 storage " + new S3Location(settings.bucket(), settings.prefix()) + " is not usable: "
                    + exception.getMessage());
        }
    }

    // reads parts of partSize until the stream ends: one PUT if it all fits in the first, otherwise a multipart
    // upload (aborted on failure, so no parts are left behind). returns the number of bytes stored
    long upload(S3Location target, InputStream content) throws IOException {
        S3Client s3 = client();
        byte[] part = content.readNBytes(partSize);
        if (part.length < partSize) {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(target.bucket())
                            .key(target.key())
                            .build(),
                    RequestBody.fromBytes(part));
            return part.length;
        }
        String uploadId = s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
                        .bucket(target.bucket())
                        .key(target.key())
                        .build())
                .uploadId();
        try {
            List<CompletedPart> parts = new ArrayList<>();
            long size = 0;
            while (part.length > 0) {
                int number = parts.size() + 1;
                String eTag = s3.uploadPart(
                                UploadPartRequest.builder()
                                        .bucket(target.bucket())
                                        .key(target.key())
                                        .uploadId(uploadId)
                                        .partNumber(number)
                                        .build(),
                                RequestBody.fromBytes(part))
                        .eTag();
                parts.add(CompletedPart.builder().partNumber(number).eTag(eTag).build());
                size += part.length;
                part = content.readNBytes(partSize);
            }
            s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(target.bucket())
                    .key(target.key())
                    .uploadId(uploadId)
                    .multipartUpload(
                            CompletedMultipartUpload.builder().parts(parts).build())
                    .build());
            return size;
        } catch (IOException | RuntimeException exception) {
            try {
                s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                        .bucket(target.bucket())
                        .key(target.key())
                        .uploadId(uploadId)
                        .build());
            } catch (SdkException abortFailure) {
                exception.addSuppressed(abortFailure);
            }
            throw exception;
        }
    }

    private Optional<Resource> open(S3Location object) {
        try {
            HeadObjectResponse head = client().headObject(HeadObjectRequest.builder()
                    .bucket(object.bucket())
                    .key(object.key())
                    .build());
            return Optional.of(new S3ObjectResource(client(), object, head.contentLength()));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private void deletePrefix(S3Location prefix) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(prefix.bucket())
                .prefix(prefix.key())
                .build();
        List<ObjectIdentifier> batch = new ArrayList<>();
        for (S3Object object : client().listObjectsV2Paginator(request).contents()) {
            batch.add(ObjectIdentifier.builder().key(object.key()).build());
            if (batch.size() == DELETE_BATCH) {
                deleteBatch(prefix.bucket(), batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            deleteBatch(prefix.bucket(), batch);
        }
    }

    private void deleteBatch(String bucket, List<ObjectIdentifier> objects) {
        client().deleteObjects(DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(objects).quiet(true).build())
                .build());
    }

    private void delete(S3Location object) {
        client().deleteObject(DeleteObjectRequest.builder()
                .bucket(object.bucket())
                .key(object.key())
                .build());
    }

    // a run on this storage has an s3:// location
    private static S3Location run(String location) {
        return S3Location.parse(location)
                .orElseThrow(() ->
                        new IllegalStateException("run location " + location + " is not on S3 (s3://<bucket>/<key>)"));
    }

    private S3Client client() {
        S3Client current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) {
                    client = clients.get();
                }
                current = client;
            }
        }
        return current;
    }

    private StsClient sts() {
        StsClient current = stsClient;
        if (current == null) {
            synchronized (this) {
                if (stsClient == null) {
                    stsClient = stsClients.get();
                }
                current = stsClient;
            }
        }
        return current;
    }

    // the connection's clients that were ever built (RunStorages closes them at shutdown)
    List<SdkAutoCloseable> builtClients() {
        List<SdkAutoCloseable> built = new ArrayList<>();
        Optional.ofNullable(client).ifPresent(built::add);
        Optional.ofNullable(stsClient).ifPresent(built::add);
        return built;
    }
}
