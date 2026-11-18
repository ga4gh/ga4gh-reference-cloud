package org.ga4gh.refcloud.api.compute.run;

import java.io.Serializable;
import java.nio.file.Path;

// an engine calling TES with its run-scoped token: it acts for the run's user, on the run's platform only, and may
// only touch files in the run's directory. serializable, as authentication principals are (the run directory is kept
// as a string for that reason)
public record RunPrincipal(String runId, String userId, String platformId, String runDirectory)
        implements Serializable {

    public Path runDirectoryPath() {
        return Path.of(runDirectory);
    }
}
