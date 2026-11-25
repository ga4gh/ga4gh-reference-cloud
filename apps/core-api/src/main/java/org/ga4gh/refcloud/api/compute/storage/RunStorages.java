package org.ga4gh.refcloud.api.compute.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputeConnections;
import org.ga4gh.refcloud.api.compute.ComputeRunConfig;
import org.ga4gh.refcloud.api.compute.platform.ComputePlatformRepository;
import org.ga4gh.refcloud.api.compute.run.ComputeRun;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.utils.SdkAutoCloseable;

// each connection's run storage (D15): shared-filesystem or s3, from its storage settings. one instance per
// connection, made on first use; a run's storage is its platform's connection's
@Component
public class RunStorages {

    private final ComputeConnections computeConnections;

    private final ComputePlatformRepository computePlatformRepository;

    private final Map<String, RunStorage> byConnection = new ConcurrentHashMap<>();

    private final ComputeRunConfig runConfig;

    public RunStorages(
            ComputeConnections computeConnections,
            ComputePlatformRepository computePlatformRepository,
            ComputeRunConfig runConfig) {
        this.computeConnections = computeConnections;
        this.computePlatformRepository = computePlatformRepository;
        this.runConfig = runConfig;
    }

    // refuses, failing startup, an S3 connection whose runs could outlive their storage credentials: they're issued
    // once, at launch, for the run's engine lifetime (ComputeRunConfig.engineLifetime), with no refresh (spec 0012)
    @PostConstruct
    public void checkConnections() {
        for (String connectionKey : computeConnections.keys()) {
            checkCredentialLifetime(
                    connectionKey, computeConnections.find(connectionKey).orElseThrow(), runConfig);
        }
    }

    static void checkCredentialLifetime(
            String connectionKey, ComputeConfig.Connection connection, ComputeRunConfig runConfig) {
        ComputeConfig.Storage storage = connection.storage();
        if (storage.type() != ComputeConfig.StorageType.S3) {
            return;
        }
        long needed = runConfig
                .engineLifetime(connection.limits().maxRunDurationSeconds())
                .toSeconds();
        if (needed > storage.runCredentialsMaxSeconds()) {
            long longestRun = storage.runCredentialsMaxSeconds()
                    - runConfig.engineLifetime(0).toSeconds();
            throw new IllegalStateException("compute connection " + connectionKey + ": a run can last "
                    + connection.limits().maxRunDurationSeconds() + " seconds (max-run-duration-seconds), and with the"
                    + " cancel grace period and margin its storage credentials would need " + needed + " seconds, more"
                    + " than they can last (storage.run-credentials-max-seconds: " + storage.runCredentialsMaxSeconds()
                    + "). Lower max-run-duration-seconds to at most " + longestRun + ".");
        }
    }

    public RunStorage forConnection(String connectionKey) {
        return byConnection.computeIfAbsent(connectionKey, this::create);
    }

    public RunStorage forRun(ComputeRun run) {
        String connectionKey = computePlatformRepository
                .findById(run.getComputePlatformId())
                .orElseThrow(() -> new IllegalStateException(
                        "run " + run.getId() + " has no compute platform " + run.getComputePlatformId()))
                .getConnectionKey();
        return forConnection(connectionKey);
    }

    @PreDestroy
    public void close() {
        for (RunStorage storage : byConnection.values()) {
            if (storage instanceof S3RunStorage s3) {
                s3.builtClients().forEach(SdkAutoCloseable::close);
            }
        }
    }

    private RunStorage create(String connectionKey) {
        ComputeConfig.Connection connection = computeConnections
                .find(connectionKey)
                .orElseThrow(() -> new IllegalStateException("no compute connection " + connectionKey));
        ComputeConfig.Storage storage = connection.storage();
        return switch (storage.type()) {
            case SHARED_FILESYSTEM ->
                new SharedFilesystemRunStorage(
                        computeConnections.storageRoot(connectionKey).orElseThrow());
            case S3 -> new S3RunStorage(storage, () -> s3Client(storage), () -> stsClient(storage));
        };
    }

    // the endpoint and path-style addressing are for S3-compatible servers (e.g. RustFS locally); AWS needs neither
    static S3Client s3Client(ComputeConfig.Storage storage) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials(storage))
                .forcePathStyle(storage.pathStyleAccess());
        if (storage.endpoint() != null) {
            builder.endpointOverride(storage.endpoint());
        }
        return builder.build();
    }

    // STS for the run credentials: an S3-compatible server takes STS requests at its S3 endpoint (RustFS does); AWS's
    // regional STS endpoint otherwise. Never the storage endpoint itself: on AWS that's an S3 endpoint (spec 0013's
    // interface endpoint), which answers STS requests with errors
    static StsClient stsClient(ComputeConfig.Storage storage) {
        StsClientBuilder builder =
                StsClient.builder().region(Region.of(storage.region())).credentialsProvider(credentials(storage));
        if (storage.stsEndpoint() != null) {
            builder.endpointOverride(storage.stsEndpoint());
        }
        return builder.build();
    }

    // the keys from the environment variables the connection names, or else the AWS SDK's default chain (environment,
    // EKS Pod Identity, IRSA, ...). the keys themselves are never in the connections file
    static AwsCredentialsProvider credentials(ComputeConfig.Storage storage) {
        if (storage.accessKeyIdEnv() == null) {
            return DefaultCredentialsProvider.builder().build();
        }
        String accessKeyId = System.getenv(storage.accessKeyIdEnv());
        String secretAccessKey = System.getenv(storage.secretAccessKeyEnv());
        if (accessKeyId == null || accessKeyId.isBlank()) {
            throw new IllegalStateException(
                    "S3 access key environment variable " + storage.accessKeyIdEnv() + " is not set");
        }
        if (secretAccessKey == null || secretAccessKey.isBlank()) {
            throw new IllegalStateException(
                    "S3 secret key environment variable " + storage.secretAccessKeyEnv() + " is not set");
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
    }
}
