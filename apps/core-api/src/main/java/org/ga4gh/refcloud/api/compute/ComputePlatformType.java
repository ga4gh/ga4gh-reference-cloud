package org.ga4gh.refcloud.api.compute;

// the compute stacks the refcloud supports (D14), named engine + executor (infrastructure). a connection's type
// decides which engine adapter and launch mechanism core-api uses. config values use the key (e.g.
// "nextflow-funnel-docker"); spring's lenient enum binding maps it to the constant
public enum ComputePlatformType {
    NEXTFLOW_FUNNEL_DOCKER("nextflow-funnel-docker", "Nextflow + Funnel (Docker)", "nextflow");

    private final String key;

    private final String displayName;

    // the workflow engine's name, as WES service-info workflow_engine_versions reports it
    private final String engine;

    ComputePlatformType(String key, String displayName, String engine) {
        this.key = key;
        this.displayName = displayName;
        this.engine = engine;
    }

    public String engine() {
        return engine;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }
}
