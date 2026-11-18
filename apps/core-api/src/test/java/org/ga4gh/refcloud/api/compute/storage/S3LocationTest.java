package org.ga4gh.refcloud.api.compute.storage;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Optional;
import org.testng.annotations.Test;

public class S3LocationTest {

    @Test
    public void locationsParseIntoBucketAndKey() {
        assertEquals(
                S3Location.parse("s3://refcloud-compute/ref/runs/r1"),
                Optional.of(new S3Location("refcloud-compute", "ref/runs/r1")));
        assertEquals(S3Location.parse("S3://bucket"), Optional.of(new S3Location("bucket", "")));
        assertEquals(
                new S3Location("bucket", "ref/runs/r1").child("outputs/x.json").toString(),
                "s3://bucket/ref/runs/r1/outputs/x.json");
        assertEquals(new S3Location("bucket", "").child("x").toString(), "s3://bucket/x");
    }

    @Test
    public void onlyS3LocationsWithABucketParse() {
        for (String location : new String[] {"/storage/runs/r1", "s3://", "s3:///key", "s3://Bucket/x", "s4://b/k"}) {
            assertEquals(S3Location.parse(location), Optional.empty(), location);
        }
        assertTrue(S3Location.isS3(" s3://x".strip()));
        assertFalse(S3Location.isS3(null));
        assertFalse(S3Location.isS3("file:///s3://x"));
    }

    @Test
    public void relativePathsAreOrdinarySegmentsOnly() {
        assertTrue(RelativePaths.isSafe("qc/summary.txt"));
        assertTrue(RelativePaths.isSafe(".command.sh"));
        for (String path : new String[] {"", "/x", "x/", "a//b", "./x", "a/../b", "..", "a\\b", "a\u0000b"}) {
            assertFalse(RelativePaths.isSafe(path), path);
        }
        assertEquals(RelativePaths.stripLeadingSlash("/report/x"), "report/x");
    }
}
