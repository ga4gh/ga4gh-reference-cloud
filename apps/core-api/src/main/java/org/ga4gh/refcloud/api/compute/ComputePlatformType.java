package org.ga4gh.refcloud.api.compute;

// the compute stacks the refcloud supports (D14), named engine + executor (infrastructure). a connection's type
// decides which engine adapter and launch mechanism core-api uses. config values use the key (e.g.
// "nextflow-funnel-docker"); spring's lenient enum binding maps it to the constant
public enum ComputePlatformType {
    NEXTFLOW_FUNNEL_DOCKER("nextflow-funnel-docker", "Nextflow + Funnel (Docker)");

    private final String key;

    private final String displayName;

    ComputePlatformType(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }
}
