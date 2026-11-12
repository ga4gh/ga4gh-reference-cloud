package org.ga4gh.refcloud.api.compute.health;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputeConnections;
import org.ga4gh.refcloud.api.compute.ComputeHealthConfig;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.testng.annotations.Test;

// how check results turn into each platform's health, with the checks stubbed
public class ComputeHealthServiceTest {

    // a checker whose result is set by the test
    private static final class StubChecker extends ConnectionChecker {
        private List<String> failures = new ArrayList<>();

        StubChecker() {
            super(new ComputeHealthConfig(Duration.ofSeconds(60), Duration.ofSeconds(1), Duration.ofSeconds(1)));
        }

        @Override
        public List<String> check(ComputeConfig.Connection connection, Path storageRoot) {
            return failures;
        }
    }

    private static ComputeConnections connections() {
        String prefix = "ga4gh.refcloud.compute.connections.c1.";
        Map<String, Object> properties = new HashMap<>();
        properties.put("ga4gh.refcloud.compute.connections-file", "/etc/refcloud/connections.yml");
        properties.put(prefix + "type", "nextflow-funnel-docker");
        properties.put(prefix + "engine.nextflow-executable", "nextflow");
        properties.put(prefix + "engine.nextflow-version", "26.10.5");
        properties.put(prefix + "engine.nf-ga4gh-version", "1.5.0");
        properties.put(prefix + "executor.tes-url", "http://funnel:8000");
        properties.put(prefix + "storage.type", "shared-filesystem");
        properties.put(prefix + "storage.root", "storage");
        properties.put(prefix + "limits.max-concurrent-runs", "1");
        properties.put(prefix + "limits.max-tasks-in-flight", "1");
        properties.put(prefix + "limits.max-cpu-cores-in-flight", "1");
        properties.put(prefix + "limits.max-memory-gb-in-flight", "1");
        properties.put(prefix + "limits.max-task-cpu-cores", "1");
        properties.put(prefix + "limits.max-task-memory-gb", "1");
        properties.put(prefix + "limits.max-task-disk-gb", "1");
        properties.put(prefix + "limits.max-run-duration-seconds", "60");
        properties.put(prefix + "limits.default-task-cpu-cores", "1");
        properties.put(prefix + "limits.default-task-memory-gb", "1");
        ComputeConfig config = new Binder(new MapConfigurationPropertySource(properties))
                .bind("ga4gh.refcloud.compute", ComputeConfig.class)
                .get();
        return new ComputeConnections(config);
    }

    @Test
    public void unhealthyUntilTheFirstCheck() {
        ComputeHealthService service = new ComputeHealthService(connections(), new StubChecker());

        ComputeHealth health = service.healthOf("c1");

        assertFalse(health.isHealthy());
        assertNull(health.checkedAt());
    }

    @Test
    public void passingChecksAreHealthy() {
        ComputeHealthService service = new ComputeHealthService(connections(), new StubChecker());

        service.checkConnections();
        ComputeHealth health = service.healthOf("c1");

        assertTrue(health.isHealthy());
        assertNotNull(health.checkedAt());
    }

    @Test
    public void anyFailedCheckIsUnhealthyAndRecovers() {
        StubChecker checker = new StubChecker();
        ComputeHealthService service = new ComputeHealthService(connections(), checker);

        checker.failures = List.of("executor is unreachable");
        service.checkConnections();
        assertFalse(service.healthOf("c1").isHealthy());

        checker.failures = List.of();
        service.checkConnections();
        assertTrue(service.healthOf("c1").isHealthy());
    }

    @Test
    public void connectionMissingFromTheFileIsUnhealthy() {
        ComputeHealthService service = new ComputeHealthService(connections(), new StubChecker());
        service.checkConnections();

        ComputeHealth health = service.healthOf("not-in-the-file");

        assertEquals(health.status(), ComputeHealth.Status.UNHEALTHY);
        assertNotNull(health.checkedAt());
    }
}
