package org.ga4gh.refcloud.api.compute.storage;

import java.util.Locale;
import java.util.Optional;

// an s3://<bucket>/<key> location, as runs on S3 storage record theirs (compute_run.run_directory) and as nf-ga4gh
// names a task's inputs and outputs. the key is kept exactly as written (S3 keys aren't percent-decoded)
public record S3Location(String bucket, String key) {

    public static final String SCHEME = "s3://";

    // whether a run's location (or a task's file url) is on S3 rather than a path
    public static boolean isS3(String location) {
        return location != null && location.regionMatches(true, 0, SCHEME, 0, SCHEME.length());
    }

    // empty if it isn't an s3:// location with a bucket
    public static Optional<S3Location> parse(String location) {
        if (!isS3(location)) {
            return Optional.empty();
        }
        String rest = location.substring(SCHEME.length());
        int slash = rest.indexOf('/');
        String bucket = slash < 0 ? rest : rest.substring(0, slash);
        if (bucket.isEmpty() || !bucket.equals(bucket.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        return Optional.of(new S3Location(bucket, slash < 0 ? "" : rest.substring(slash + 1)));
    }

    // the location of a path under this one, e.g. outputs/report.json under a run's location
    public S3Location child(String relativePath) {
        return new S3Location(bucket, key.isEmpty() ? relativePath : key + "/" + relativePath);
    }

    @Override
    public String toString() {
        return SCHEME + bucket + "/" + key;
    }
}
