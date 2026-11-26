package org.ga4gh.refcloud.api.compute.storage;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputeConnections;
import org.ga4gh.refcloud.api.compute.ComputeRunConfig;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.testng.annotations.Test;

public class RunStoragesTest {

    private static final String PREFIX = "ga4gh.refcloud.compute.connections.c1.";

    // a 2 minute cancel grace period, as in application.yml; the margin is 5 minutes
    private static final ComputeRunConfig RUN_CONFIG = new ComputeRunConfig(
            URI.create("http://localhost:8080"), Duration.ofSeconds(5), Duration.ofMinutes(2), Duration.ofSeconds(30));

    // a kubernetes connection on S3 storage whose runs can last maxRunSeconds, with credentials of up to
    // credentialSeconds (unset: the default, 3600)
    private static ComputeConnections connections(int maxRunSeconds, Integer credentialSeconds) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("ga4gh.refcloud.compute.connections-file", "/etc/refcloud/compute/connections.yml");
        properties.put(PREFIX + "type", "nextflow-funnel-kubernetes");
        properties.put(PREFIX + "engine.image", "refcloud-nextflow:dev");
        properties.put(PREFIX + "engine.namespace", "refcloud-compute");
        properties.put(PREFIX + "engine.nextflow-version", "26.10.5");
        properties.put(PREFIX + "engine.nf-ga4gh-version", "1.5.0");
        properties.put(PREFIX + "executor.tes-url", "http://funnel:8000");
        properties.put(PREFIX + "storage.type", "s3");
        properties.put(PREFIX + "storage.bucket", "refcloud-compute");
        properties.put(PREFIX + "storage.region", "us-east-1");
        properties.put(PREFIX + "storage.run-role-arn", "arn:aws:iam::123456789012:role/refcloud-compute-run");
        if (credentialSeconds != null) {
            properties.put(PREFIX + "storage.run-credentials-max-seconds", String.valueOf(credentialSeconds));
        }
        properties.put(PREFIX + "limits.max-concurrent-runs", "1");
        properties.put(PREFIX + "limits.max-tasks-in-flight", "1");
        properties.put(PREFIX + "limits.max-cpu-cores-in-flight", "1");
        properties.put(PREFIX + "limits.max-memory-gb-in-flight", "1");
        properties.put(PREFIX + "limits.max-task-cpu-cores", "1");
        properties.put(PREFIX + "limits.max-task-memory-gb", "1");
        properties.put(PREFIX + "limits.max-task-disk-gb", "1");
        properties.put(PREFIX + "limits.max-run-duration-seconds", String.valueOf(maxRunSeconds));
        properties.put(PREFIX + "limits.default-task-cpu-cores", "1");
        properties.put(PREFIX + "limits.default-task-memory-gb", "1");
        properties.put(PREFIX + "limits.max-run-inputs-gb", "10");
        return new ComputeConnections(new Binder(new MapConfigurationPropertySource(properties))
                .bind("ga4gh.refcloud.compute", ComputeConfig.class)
                .get());
    }

    // on AWS, credentials last at most an hour: 45-minute runs fit (2700 + 120 + 300 = 3120), 53 minutes is the most
    @Test
    public void runsThatFitTheirCredentialsAreAccepted() {
        new RunStorages(connections(2700, null), null, RUN_CONFIG).checkConnections();
        new RunStorages(connections(3180, null), null, RUN_CONFIG).checkConnections();
        // RustFS issues credentials for up to 12 hours, so local runs keep their hour
        new RunStorages(connections(3600, 43200), null, RUN_CONFIG).checkConnections();
    }

    @Test
    public void runsThatCouldOutliveTheirCredentialsFailStartup() {
        IllegalStateException exception = expectThrows(
                IllegalStateException.class,
                () -> new RunStorages(connections(3600, null), null, RUN_CONFIG).checkConnections());

        assertTrue(exception.getMessage().contains("compute connection c1"), exception.getMessage());
        assertTrue(exception.getMessage().contains("would need 4020 seconds"), exception.getMessage());
        assertTrue(exception.getMessage().contains("at most 3180"), exception.getMessage());
    }

    @Test
    public void engineLifetimeIsTheRunPlusGraceAndMargin() {
        assertEquals(RUN_CONFIG.engineLifetime(2700), Duration.ofSeconds(2700 + 120 + 300));
    }

    private static ComputeConfig.Storage s3(String endpoint, String stsEndpoint) {
        return new ComputeConfig.Storage(
                ComputeConfig.StorageType.S3,
                null,
                "refcloud-compute",
                null,
                "us-east-2",
                endpoint == null ? null : URI.create(endpoint),
                stsEndpoint == null ? null : URI.create(stsEndpoint),
                endpoint != null,
                null,
                null,
                "arn:aws:iam::123456789012:role/refcloud-compute-run",
                null);
    }

    // RustFS issues run credentials at its S3 endpoint, so its connection names it for STS too
    @Test
    public void stsGoesWhereTheConnectionSays() {
        try (var sts =
                RunStorages.stsClient(s3("http://rustfs.refcloud.svc:9000", "http://rustfs.refcloud.svc:9000"))) {
            assertEquals(
                    sts.serviceClientConfiguration().endpointOverride(),
                    java.util.Optional.of(URI.create("http://rustfs.refcloud.svc:9000")));
        }
    }

    // through an S3 interface endpoint on AWS (spec 0013): S3 goes there, STS to AWS's own regional endpoint, never to
    // the S3 endpoint (which answers STS requests with errors)
    @Test
    public void stsNeverUsesTheS3Endpoint() {
        String endpoint = "https://bucket.vpce-0abc-xyz.s3.us-east-2.vpce.amazonaws.com";
        try (var s3 = RunStorages.s3Client(s3(endpoint, null));
                var sts = RunStorages.stsClient(s3(endpoint, null))) {
            assertEquals(
                    s3.serviceClientConfiguration().endpointOverride(), java.util.Optional.of(URI.create(endpoint)));
            assertEquals(sts.serviceClientConfiguration().endpointOverride(), java.util.Optional.empty());
        }
    }
}
