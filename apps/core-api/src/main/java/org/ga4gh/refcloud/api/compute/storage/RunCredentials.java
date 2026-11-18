package org.ga4gh.refcloud.api.compute.storage;

import java.time.Instant;

// temporary S3 credentials for one run's engine, limited to the run's own prefix (spec 0012, Task 3). they reach the
// engine only through its per-run Secret, like its TES token, and are never stored or logged: toString leaves out
// everything but the key id's first characters and when they expire
public record RunCredentials(String accessKeyId, String secretAccessKey, String sessionToken, Instant expiration) {

    // the environment variables the AWS SDK (Nextflow's nf-amazon) and the Job's own S3 requests read
    public static final String ACCESS_KEY_ID_VARIABLE = "AWS_ACCESS_KEY_ID";

    public static final String SECRET_ACCESS_KEY_VARIABLE = "AWS_SECRET_ACCESS_KEY";

    public static final String SESSION_TOKEN_VARIABLE = "AWS_SESSION_TOKEN";

    @Override
    public String toString() {
        String keyId = accessKeyId == null ? "" : accessKeyId.substring(0, Math.min(4, accessKeyId.length()));
        return "RunCredentials[accessKeyId=" + keyId + "..., expiration=" + expiration + "]";
    }
}
