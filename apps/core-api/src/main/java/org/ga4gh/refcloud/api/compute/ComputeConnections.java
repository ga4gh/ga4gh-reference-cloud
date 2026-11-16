package org.ga4gh.refcloud.api.compute;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// read access to the configured compute connections, with relative paths resolved from the connections file's
// directory (so core-api and the admin CLI, which run from different working directories, agree on them)
@Slf4j
@Component
public class ComputeConnections {

    private final ComputeConfig computeConfig;

    private final Path connectionsFileDirectory;

    public ComputeConnections(ComputeConfig computeConfig) {
        this.computeConfig = computeConfig;
        this.connectionsFileDirectory = Path.of(computeConfig.connectionsFile())
                .toAbsolutePath()
                .normalize()
                .getParent();
        if (computeConfig.connections().isEmpty()) {
            log.info("no compute connections configured ({}); compute is off", computeConfig.connectionsFile());
        } else {
            log.info(
                    "compute connections configured from {}: {}",
                    computeConfig.connectionsFile(),
                    computeConfig.connections().keySet());
        }
    }

    public Set<String> keys() {
        return computeConfig.connections().keySet();
    }

    public Optional<ComputeConfig.Connection> find(String connectionKey) {
        return Optional.ofNullable(computeConfig.connections().get(connectionKey));
    }

    // the connection's storage root as an absolute path
    public Optional<Path> storageRoot(String connectionKey) {
        return find(connectionKey)
                .map(connection -> connectionsFileDirectory
                        .resolve(connection.storage().root())
                        .normalize());
    }
}
