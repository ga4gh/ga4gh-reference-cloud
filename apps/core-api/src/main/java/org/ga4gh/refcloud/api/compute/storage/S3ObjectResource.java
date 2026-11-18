package org.ga4gh.refcloud.api.compute.storage;

import java.io.InputStream;
import org.springframework.core.io.AbstractResource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

// an S3 object as a download body (WES outputs and logs, spec 0012): its size is known up front (from a HEAD request),
// and each read is a new GET, streamed through core-api (decision 4). Spring answers a Range request by reading from
// the start and skipping, which is correct but not efficient; passing ranges to S3 goes with presigned redirects
// (Future Directions)
class S3ObjectResource extends AbstractResource {

    private final S3Client client;

    private final S3Location location;

    private final long size;

    S3ObjectResource(S3Client client, S3Location location, long size) {
        this.client = client;
        this.location = location;
        this.size = size;
    }

    @Override
    public InputStream getInputStream() {
        return client.getObject(GetObjectRequest.builder()
                .bucket(location.bucket())
                .key(location.key())
                .build());
    }

    @Override
    public long contentLength() {
        return size;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public String getFilename() {
        String key = location.key();
        return key.substring(key.lastIndexOf('/') + 1);
    }

    @Override
    public String getDescription() {
        return "S3 object " + location;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof S3ObjectResource resource && resource.location.equals(location);
    }

    @Override
    public int hashCode() {
        return location.hashCode();
    }
}
