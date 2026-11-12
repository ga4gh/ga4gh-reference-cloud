package org.ga4gh.refcloud.api.compute.health;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputeConnections;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

// connection health (D10). every configured connection is checked on a schedule, starting at startup; platforms on
// the same connection share its result. results live in memory, per core-api instance. a platform whose connection
// isn't in the connections file is unhealthy. connection health never feeds the actuator liveness/readiness probes:
// an unhealthy connection blocks only the platforms it backs
@Slf4j
@Service
public class ComputeHealthService {

    private final ComputeConnections computeConnections;

    private final ConnectionChecker connectionChecker;

    private final Map<String, ComputeHealth> healthByConnection = new ConcurrentHashMap<>();

    // the latest failure reasons per connection, so each change is logged once instead of every round
    private final Map<String, List<String>> failuresByConnection = new ConcurrentHashMap<>();

    private volatile Instant lastRoundCompletedAt;

    public ComputeHealthService(ComputeConnections computeConnections, ConnectionChecker connectionChecker) {
        this.computeConnections = computeConnections;
        this.connectionChecker = connectionChecker;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${ga4gh.refcloud.compute.health.check-interval}")
    public void checkConnections() {
        for (String connectionKey : computeConnections.keys()) {
            ComputeConfig.Connection connection =
                    computeConnections.find(connectionKey).orElseThrow();
            List<String> failures = connectionChecker.check(
                    connection, computeConnections.storageRoot(connectionKey).orElseThrow());
            Instant checkedAt = Instant.now();
            healthByConnection.put(
                    connectionKey,
                    new ComputeHealth(
                            failures.isEmpty() ? ComputeHealth.Status.HEALTHY : ComputeHealth.Status.UNHEALTHY,
                            checkedAt));
            logChange(connectionKey, failures);
        }
        lastRoundCompletedAt = Instant.now();
    }

    // the health of the platforms backed by this connection
    public ComputeHealth healthOf(String connectionKey) {
        if (computeConnections.find(connectionKey).isEmpty()) {
            return new ComputeHealth(ComputeHealth.Status.UNHEALTHY, lastRoundCompletedAt);
        }
        return healthByConnection.getOrDefault(connectionKey, ComputeHealth.NOT_YET_CHECKED);
    }

    private void logChange(String connectionKey, List<String> failures) {
        List<String> previous = failuresByConnection.put(connectionKey, failures);
        if (failures.equals(previous)) {
            return;
        }
        if (failures.isEmpty()) {
            log.info("compute connection {} is healthy", connectionKey);
        } else {
            log.warn("compute connection {} is unhealthy: {}", connectionKey, String.join("; ", failures));
        }
    }
}
