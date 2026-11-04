package org.ga4gh.refcloud.api.drs.awss3accessobject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

// streams content from open access S3 buckets. clients are cached per region and kept open,
// since the returned streams are consumed after this call returns
@Component
public class AwsS3ObjectReader {

    private final Map<String, S3Client> clientsByRegion = new ConcurrentHashMap<>();

    // first and last are 0-based and inclusive, as in an HTTP Range header
    public ResponseInputStream<GetObjectResponse> readByteRange(AwsS3AccessObject awsS3AccessObject, long first, long last) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(awsS3AccessObject.getBucket())
            .key(toObjectKey(awsS3AccessObject.getKey()))
            .range("bytes=" + first + "-" + last)
            .build();

        return getClient(awsS3AccessObject.getRegion()).getObject(getObjectRequest);
    }

    @PreDestroy
    public void closeClients() {
        clientsByRegion.values().forEach(S3Client::close);
    }

    private S3Client getClient(String region) {
        return clientsByRegion.computeIfAbsent(region, r -> S3Client.builder()
            .region(Region.of(r))
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .build());
    }

    // stored keys begin with "/" (see access URL generation in DrsObjectService), S3 keys do not
    private String toObjectKey(String key) {
        return key.startsWith("/") ? key.substring(1) : key;
    }
}
