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
        properties.put(PREFIX + "storage.type", "s3");

        expectThrows(BindException.class, () -> bind(properties));
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
}
