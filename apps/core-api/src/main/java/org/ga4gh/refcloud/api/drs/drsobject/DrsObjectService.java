package org.ga4gh.refcloud.api.drs.drsobject;

import java.util.Set;
import java.util.stream.Collectors;
import org.ga4gh.refcloud.api.drs.DrsConfig;
import org.ga4gh.refcloud.api.drs.accessmethod.AccessMethodResponseDTO;
import org.ga4gh.refcloud.api.drs.accessmethod.AccessMethodType;
import org.ga4gh.refcloud.api.drs.awss3accessobject.AwsS3AccessObject;
import org.ga4gh.refcloud.api.drs.drsobjectalias.DrsObjectAlias;
import org.ga4gh.refcloud.api.drs.drsobjectalias.DrsObjectAliasId;
import org.ga4gh.refcloud.api.drs.drsobjectchecksum.DrsObjectChecksumResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import java.net.URI;
import java.time.Duration;

@Service
public class DrsObjectService {

    private final DrsObjectRepository drsObjectRepository;

    private final DrsConfig drsConfig;

    public DrsObjectService(DrsObjectRepository drsObjectRepository, DrsConfig drsConfig) {
        this.drsObjectRepository = drsObjectRepository;
        this.drsConfig = drsConfig;
    }

    @Transactional(readOnly = true)
    public DrsObjectResponseDTO getDrsObjectById(String id) {
        DrsObject drsObject = drsObjectRepository.findById(id).orElse(null);
        if (drsObject == null) {
            return null;
        }
        return convertToResponseDTO(drsObject);
    }

    private DrsObjectResponseDTO convertToResponseDTO(DrsObject drsObject) {
        Set<DrsObjectChecksumResponseDTO> checksumDtos = drsObject.getChecksums()
            .stream()
            .map(checksum -> new DrsObjectChecksumResponseDTO(checksum.getChecksum(), checksum.getType()))
            .collect(Collectors.toSet());

        Set<AccessMethodResponseDTO> accessMethodDtos = drsObject.getAwsS3AccessObjects()
            .stream()
            .map(s3Object -> new AccessMethodResponseDTO(
                AccessMethodType.https,
                generateAccessUrlForOpenAccessS3Object(s3Object),
                "aws",
                s3Object.getRegion(),
                true
            ))
            .collect(Collectors.toSet());

        Set<String> aliasDtos = drsObject.getAliases()
                .stream()
                .map(DrsObjectAlias::getId)
                .map(DrsObjectAliasId::getAlias)
                .collect(Collectors.toSet());

        return new DrsObjectResponseDTO(
            drsObject.getId(),
            drsObject.getName(),
            generateDrsUri(drsObject.getId()),
            drsObject.getSize(),
            drsObject.getCreatedTime(),
            drsObject.getUpdatedTime(),
            drsObject.getVersion(),
            drsObject.getMimeType(),
            checksumDtos,
            accessMethodDtos,
            drsObject.getDescription(),
            aliasDtos
        );
    }

    private String generateDrsUri(String id) {
        return "drs://" + drsConfig.hostDomain() + "/" + id;
    }

    private String generateAccessUrlForOpenAccessS3Object(AwsS3AccessObject awsS3AccessObject) {
        return "https://" +
            awsS3AccessObject.getBucket() +
            ".s3." +
            awsS3AccessObject.getRegion() +
            ".amazonaws.com" +
            awsS3AccessObject.getKey();
    }

    // TODO: implement this method once ready to deal with private S3 buckets. Currently only working with open access data
    /*
    private String generateSignedUrlForS3Object(AwsS3AccessObject awsS3AccessObject) {
        long expirationInMinutes = 60;
        Region region = Region.of(awsS3AccessObject.getRegion());

        try (S3Presigner s3Presigner = S3Presigner.builder().region(region).build()) {

            // 3. Define the target file and bucket target
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(awsS3AccessObject.getBucket())
                    .key(awsS3AccessObject.getKey())
                    .build();

            // 4. Configure the signature request
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expirationInMinutes))
                    .getObjectRequest(getObjectRequest)
                    .build();

            // 5. Generate the URL
            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

            return presignedRequest.url().toString();
        }
    }
    */
}
