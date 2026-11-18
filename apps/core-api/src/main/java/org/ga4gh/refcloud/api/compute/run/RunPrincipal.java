package org.ga4gh.refcloud.api.compute.run;

import java.io.Serializable;

// an engine calling TES with its run-scoped token: it acts for the run's user, on the run's platform only, and may
// only touch files in the run's location (a directory, or an s3:// prefix: spec 0012). serializable, as
// authentication principals are
public record RunPrincipal(String runId, String userId, String platformId, String runDirectory)
        implements Serializable {}
