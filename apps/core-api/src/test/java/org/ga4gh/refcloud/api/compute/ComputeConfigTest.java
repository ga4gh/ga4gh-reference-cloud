package org.ga4gh.refcloud.api.compute;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

// binds ComputeConfig the way spring does at startup, from the real local connections file and from small inline
// configs, and checks validation and storage root resolution
public class ComputeConfigTest {

    // tests run from apps/core-api, like bootRun, so this is application.yml's default path
    private static final String LOCAL_CONNECTIONS_FILE = "../../deploy/local/compute/connections.yml";

    private static final String PREFIX = "ga4gh.refcloud.compute.connections.c1.";

    private final jakarta.validation.ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();

    private final Validator validator = validatorFactory.getValidator();

    @AfterClass
    public void closeValidatorFactory() {
        validatorFactory.close();
    }

    private ComputeConfig bindLocalConnectionsFile() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("connections", new FileSystemResource(LOCAL_CONNECTIONS_FILE));
        Map<String, Object> connectionsFile = Map.of("ga4gh.refcloud.compute.connections-file", LOCAL_CONNECTIONS_FILE);
        Binder binder = new Binder(
                ConfigurationPropertySources.from(sources).iterator().next(),
                new MapConfigurationPropertySource(connectionsFile));
        return binder.bind("ga4gh.refcloud.compute", ComputeConfig.class).get();
    }

    // a complete, valid connection named c1; tests override or remove single properties
    private Map<String, Object> validConnection() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("ga4gh.refcloud.compute.connections-file", "/etc/refcloud/compute/connections.yml");
        properties.put(PREFIX + "type", "nextflow-funnel-docker");
        properties.put(PREFIX + "engine.nextflow-executable", "nextflow");
        properties.put(PREFIX + "engine.nextflow-version", "26.10.5");
        properties.put(PREFIX + "engine.nf-ga4gh-version", "1.5.0");
        properties.put(PREFIX + "executor.tes-url", "http://funnel:8000");
        properties.put(PREFIX + "storage.type", "shared-filesystem");
        properties.put(PREFIX + "storage.root", "storage");
        properties.put(PREFIX + "limits.max-concurrent-runs", "2");
        properties.put(PREFIX + "limits.max-tasks-in-flight", "4");
        properties.put(PREFIX + "limits.max-cpu-cores-in-flight", "4");
        properties.put(PREFIX + "limits.max-memory-gb-in-flight", "8");
        properties.put(PREFIX + "limits.max-task-cpu-cores", "2");
        properties.put(PREFIX + "limits.max-task-memory-gb", "4");
        properties.put(PREFIX + "limits.max-task-disk-gb", "10");
        properties.put(PREFIX + "limits.max-run-duration-seconds", "600");
        properties.put(PREFIX + "limits.default-task-cpu-cores", "1");
        properties.put(PREFIX + "limits.default-task-memory-gb", "1");
        properties.put(PREFIX + "limits.max-run-inputs-gb", "10");
        return properties;
    }

    private ComputeConfig bind(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind("ga4gh.refcloud.compute", ComputeConfig.class)
                .get();
    }

    @Test
    public void localConnectionsFileBindsAndIsValid() throws IOException {
        ComputeConfig config = bindLocalConnectionsFile();

        assertEquals(config.connections().keySet(), Set.of("nextflow-funnel-docker-local"));
        ComputeConfig.Connection connection = config.connections().get("nextflow-funnel-docker-local");
        assertEquals(connection.type(), ComputePlatformType.NEXTFLOW_FUNNEL_DOCKER);
        assertEquals(connection.executor().tesUrl(), URI.create("http://localhost:8000"));
        assertEquals(connection.storage().type(), ComputeConfig.StorageType.SHARED_FILESYSTEM);
        assertEquals(connection.limits().maxTaskMemoryGb(), new BigDecimal("4"));
        assertEquals(connection.limits().monthlyCoreHoursBudget(), null);
        assertTrue(validator.validate(config).isEmpty(), "local connections file has constraint violations");
    }

    @Test
    public void localStorageRootResolvesFromTheConnectionsFileDirectory() throws IOException {
        ComputeConnections connections = new ComputeConnections(bindLocalConnectionsFile());

        Path repoRoot = Path.of("../..").toAbsolutePath().normalize();
        assertEquals(
                connections.storageRoot("nextflow-funnel-docker-local").orElseThrow(),
                repoRoot.resolve("compute/nextflow-funnel-docker"));
    }

    @Test
    public void absoluteStorageRootIsKept() {
        Map<String, Object> properties = validConnection();
        properties.put(PREFIX + "storage.root", "/data/refcloud");

        assertEquals(
                new ComputeConnections(bind(properties)).storageRoot("c1").orElseThrow(), Path.of("/data/refcloud"));
    }

    @Test
    public void unknownConnectionHasNoStorageRoot() {
        assertFalse(new ComputeConnections(bind(validConnection()))
                .storageRoot("missing")
                .isPresent());
    }

    @Test
    public void noConnectionsMeansComputeIsOff() {
        ComputeConfig config = bind(Map.of("ga4gh.refcloud.compute.connections-file", "/missing/connections.yml"));

        assertTrue(config.connections().isEmpty());
        assertTrue(validator.validate(config).isEmpty());
    }

    @Test
    public void unknownPlatformTypeFailsBinding() {
        Map<String, Object> properties = validConnection();
        properties.put(PREFIX + "type", "cromwell-funnel-docker");

        expectThrows(BindException.class, () -> bind(properties));
    }

    @Test
    public void unknownStorageTypeFailsBinding() {
        Map<String, Object> properties = validConnection();
        properties.put(PREFIX + "storage.type", "gcs");

        expectThrows(BindException.class, () -> bind(properties));
    }

    // a valid kubernetes connection on S3 storage (spec 0012), as helm mode's local one
    private Map<String, Object> validS3Connection() {
        Map<String, Object> properties = validConnection();
        properties.put(PREFIX + "type", "nextflow-funnel-kubernetes");
        properties.remove(PREFIX + "engine.nextflow-executable");
        properties.put(PREFIX + "engine.image", "refcloud-nextflow:dev");
        properties.put(PREFIX + "engine.namespace", "refcloud-compute");
        properties.remove(PREFIX + "storage.root");
        properties.put(PREFIX + "storage.type", "s3");
        properties.put(PREFIX + "storage.bucket", "refcloud-compute");
        properties.put(PREFIX + "storage.prefix", "refcloud/");
        properties.put(PREFIX + "storage.region", "us-east-1");
        properties.put(PREFIX + "storage.endpoint", "http://rustfs.refcloud-compute.svc:9000");
        properties.put(PREFIX + "storage.path-style-access", "true");
        properties.put(PREFIX + "storage.access-key-id-env", "REFCLOUD_COMPUTE_S3_ACCESS_KEY_ID");
        properties.put(PREFIX + "storage.secret-access-key-env", "REFCLOUD_COMPUTE_S3_SECRET_ACCESS_KEY");
        properties.put(PREFIX + "storage.run-role-arn", "arn:aws:iam::000000000000:role/refcloud-compute-run");
        properties.put(PREFIX + "storage.run-credentials-max-seconds", "43200");
        return properties;
    }

    @Test
    public void s3StorageBindsAndIsValid() {
        ComputeConfig config = bind(validS3Connection());

        ComputeConfig.Storage storage = config.connections().get("c1").storage();
        assertEquals(storage.type(), ComputeConfig.StorageType.S3);
        assertEquals(storage.bucket(), "refcloud-compute");
        assertEquals(storage.prefix(), "refcloud/");
        assertEquals(storage.endpoint(), URI.create("http://rustfs.refcloud-compute.svc:9000"));
        assertTrue(storage.pathStyleAccess());
        assertEquals(storage.runRoleArn(), "arn:aws:iam::000000000000:role/refcloud-compute-run");
        assertEquals(storage.runCredentialsMaxSeconds(), Integer.valueOf(43200));
        assertTrue(
                validator.validate(config).isEmpty(), validator.validate(config).toString());
        // S3 storage has no root
        assertFalse(new ComputeConnections(config).storageRoot("c1").isPresent());
    }

    // on AWS: the bucket, its region and the role runs' credentials come from; credentials last up to an hour
    @Test
    public void s3StorageOnAwsNeedsOnlyABucketRegionAndRunRole() {
        Map<String, Object> properties = validS3Connection();
        for (String setting : List.of(
                "prefix",
                "endpoint",
                "path-style-access",
                "access-key-id-env",
                "secret-access-key-env",
                "run-credentials-max-seconds")) {
            properties.remove(PREFIX + "storage." + setting);
        }
        ComputeConfig config = bind(properties);

        ComputeConfig.Storage storage = config.connections().get("c1").storage();
        assertEquals(storage.prefix(), "");
        assertFalse(storage.pathStyleAccess());
        assertEquals(storage.runCredentialsMaxSeconds(), Integer.valueOf(3600));
        assertTrue(validator.validate(config).isEmpty());
    }

    @Test
    public void storageSettingsMustMatchTheType() {
        Map<String, Object> noBucket = validS3Connection();
        noBucket.remove(PREFIX + "storage.bucket");
        Map<String, Object> rootOnS3 = validS3Connection();
        rootOnS3.put(PREFIX + "storage.root", "storage");
        Map<String, Object> oneCredential = validS3Connection();
        oneCredential.remove(PREFIX + "storage.secret-access-key-env");
        Map<String, Object> bucketOnSharedFilesystem = validConnection();
        bucketOnSharedFilesystem.put(PREFIX + "storage.bucket", "refcloud-compute");
        Map<String, Object> noRunRole = validS3Connection();
        noRunRole.remove(PREFIX + "storage.run-role-arn");
        Map<String, Object> credentialsOnSharedFilesystem = validConnection();
        credentialsOnSharedFilesystem.put(PREFIX + "storage.run-credentials-max-seconds", "3600");

        for (Map<String, Object> properties : List.of(
                noBucket,
                rootOnS3,
                oneCredential,
                bucketOnSharedFilesystem,
                noRunRole,
                credentialsOnSharedFilesystem)) {
            Set<ConstraintViolation<ComputeConfig>> violations = validator.validate(bind(properties));
            assertEquals(violations.size(), 1, violations.toString());
            assertTrue(violations.iterator().next().getMessage().contains("storage settings"));
        }
    }

    @Test
    public void dockerTypeRefusesS3Storage() {
        Map<String, Object> properties = validS3Connection();
        properties.put(PREFIX + "type", "nextflow-funnel-docker");
        properties.put(PREFIX + "engine.nextflow-executable", "nextflow");
        properties.remove(PREFIX + "engine.image");
        properties.remove(PREFIX + "engine.namespace");

        Set<ConstraintViolation<ComputeConfig>> violations = validator.validate(bind(properties));

        assertEquals(violations.size(), 1, violations.toString());
        assertTrue(violations.iterator().next().getMessage().contains("only shared-filesystem"));
    }

    @Test
    public void s3BucketAndPrefixMustBeValid() {
        for (String[] invalid : new String[][] {
            {"bucket", "Refcloud_Compute"},
            {"bucket", "ab"},
            {"prefix", "/refcloud/"},
            {"prefix", "refcloud"},
            {"prefix", "a/../b/"},
            {"prefix", "a//"},
            {"access-key-id-env", "1BAD"},
            {"run-role-arn", "refcloud-compute-run"},
            {"run-role-arn", "arn:aws:iam::123:role/x"},
            {"run-credentials-max-seconds", "600"},
            {"run-credentials-max-seconds", "50000"},
        }) {
            Map<String, Object> properties = validS3Connection();
            properties.put(PREFIX + "storage." + invalid[0], invalid[1]);

            assertFalse(validator.validate(bind(properties)).isEmpty(), invalid[0] + "=" + invalid[1]);
        }
        Map<String, Object> nested = validS3Connection();
        nested.put(PREFIX + "storage.prefix", "org-a/refcloud/");
        assertTrue(validator.validate(bind(nested)).isEmpty());
    }

    @Test
    public void missingRequiredLimitIsAViolation() {
        Map<String, Object> properties = validConnection();
        properties.remove(PREFIX + "limits.max-task-memory-gb");

        Set<ConstraintViolation<ComputeConfig>> violations = validator.validate(bind(properties));

        assertEquals(violations.size(), 1);
        assertTrue(violations.iterator().next().getPropertyPath().toString().endsWith("maxTaskMemoryGb"));
    }

    @Test
    public void nonPositiveLimitIsAViolation() {
        Map<String, Object> properties = validConnection();
        properties.put(PREFIX + "limits.max-concurrent-runs", "0");

        assertEquals(validator.validate(bind(properties)).size(), 1);
    }

    @Test
    public void defaultTaskResourcesAboveTheCeilingIsAViolation() {
        Map<String, Object> properties = validConnection();
        properties.put(PREFIX + "limits.default-task-memory-gb", "8");

        Set<ConstraintViolation<ComputeConfig>> violations = validator.validate(bind(properties));

        assertEquals(violations.size(), 1);
        assertTrue(violations.iterator().next().getMessage().contains("default task resources"));
    }

    @Test
    public void invalidConnectionKeyIsAViolation() {
        Map<String, Object> properties = new HashMap<>();
        validConnection().forEach((key, value) -> properties.put(key.replace(".c1.", ".Bad_Key."), value));

        assertFalse(validator.validate(bind(properties)).isEmpty());
    }

    // a kubernetes connection kept to a node pool, with an SELinux level for its pods (spec 0013), as the helm chart
    // writes it: label keys keep their dots and slashes, since they're values here, not map keys
    private Map<String, Object> placedConnection() {
        Map<String, Object> properties = validS3Connection();
        properties.put(PREFIX + "engine.node-selector[0].key", "refcloud.ga4gh.org/compute");
        properties.put(PREFIX + "engine.node-selector[0].value", "staging");
        properties.put(PREFIX + "engine.tolerations[0].key", "refcloud.ga4gh.org/compute");
        properties.put(PREFIX + "engine.tolerations[0].operator", "Equal");
        properties.put(PREFIX + "engine.tolerations[0].value", "staging");
        properties.put(PREFIX + "engine.tolerations[0].effect", "NoSchedule");
        properties.put(PREFIX + "engine.selinux-level", "s0:c123,c456");
        return properties;
    }

    @Test
    public void enginePlacementBindsAndIsValid() {
        ComputeConfig config = bind(placedConnection());

        ComputeConfig.Engine engine = config.connections().get("c1").engine();
        assertEquals(
                engine.nodeSelector(), List.of(new ComputeConfig.NodeLabel("refcloud.ga4gh.org/compute", "staging")));
        assertEquals(
                engine.tolerations(),
                List.of(new ComputeConfig.Toleration("refcloud.ga4gh.org/compute", "Equal", "staging", "NoSchedule")));
        assertEquals(engine.selinuxLevel(), "s0:c123,c456");
        assertTrue(
                validator.validate(config).isEmpty(), validator.validate(config).toString());
    }

    @Test
    public void enginePlacementIsOptional() {
        ComputeConfig.Engine engine =
                bind(validS3Connection()).connections().get("c1").engine();

        assertEquals(engine.nodeSelector(), List.of());
        assertEquals(engine.tolerations(), List.of());
        assertEquals(engine.selinuxLevel(), null);
    }

    @Test
    public void invalidEnginePlacementIsAViolation() {
        Map<String, String> invalid = Map.of(
                "engine.node-selector[0].key", "not a label",
                "engine.node-selector[0].value", "not a value!",
                "engine.tolerations[0].operator", "Matches",
                "engine.tolerations[0].effect", "NoRun",
                "engine.selinux-level", "s0:c123;c456");
        for (Map.Entry<String, String> setting : invalid.entrySet()) {
            Map<String, Object> properties = placedConnection();
            properties.put(PREFIX + setting.getKey(), setting.getValue());

            assertEquals(validator.validate(bind(properties)).size(), 1, setting.toString());
        }
    }

    // a toleration with no key would tolerate every taint, and Exists compares no value
    @Test
    public void tolerationNeedsAKeyAndExistsTakesNoValue() {
        Map<String, Object> noKey = placedConnection();
        noKey.remove(PREFIX + "engine.tolerations[0].key");
        Map<String, Object> existsWithValue = placedConnection();
        existsWithValue.put(PREFIX + "engine.tolerations[0].operator", "Exists");

        assertEquals(validator.validate(bind(noKey)).size(), 1);
        Set<ConstraintViolation<ComputeConfig>> violations = validator.validate(bind(existsWithValue));
        assertEquals(violations.size(), 1);
        assertTrue(violations.iterator().next().getMessage().contains("Exists takes no value"));
    }

    @Test
    public void nodeSelectorNamingALabelTwiceIsAViolation() {
        Map<String, Object> properties = placedConnection();
        properties.put(PREFIX + "engine.node-selector[1].key", "refcloud.ga4gh.org/compute");
        properties.put(PREFIX + "engine.node-selector[1].value", "prod");

        Set<ConstraintViolation<ComputeConfig>> violations = validator.validate(bind(properties));
        assertEquals(violations.size(), 1);
        assertTrue(violations.iterator().next().getMessage().contains("more than once"));
    }
}
