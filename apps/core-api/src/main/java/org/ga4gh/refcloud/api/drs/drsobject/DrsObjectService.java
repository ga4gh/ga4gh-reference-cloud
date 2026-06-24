package org.ga4gh.refcloud.api.drs.drsobject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.ga4gh.refcloud.api.drs.DrsConfig;
import org.ga4gh.refcloud.api.drs.accessmethod.AccessMethodResponseDTO;
import org.ga4gh.refcloud.api.drs.accessmethod.AccessMethodType;
import org.ga4gh.refcloud.api.drs.authinfo.MultiDrsObjectAuthInfoResponseDTO;
import org.ga4gh.refcloud.api.drs.authinfo.MultiDrsObjectAuthInfoSummaryResponseDTO;
import org.ga4gh.refcloud.api.drs.authinfo.MultiDrsObjectAuthInfoUnresolvedIdSetResponseDTO;
import org.ga4gh.refcloud.api.drs.authinfo.MultiDrsObjectResponseDTO;
import org.ga4gh.refcloud.api.drs.authinfo.SingleDrsObjectAuthInfoResponseDTO;
import org.ga4gh.refcloud.api.drs.authinfo.SupportedType;
import org.ga4gh.refcloud.api.drs.awss3accessobject.AwsS3AccessObject;
import org.ga4gh.refcloud.api.drs.drsobjectalias.DrsObjectAlias;
import org.ga4gh.refcloud.api.drs.drsobjectalias.DrsObjectAliasId;
import org.ga4gh.refcloud.api.drs.drsobjectchecksum.DrsObjectChecksumResponseDTO;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertion;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertionService;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportVisaAssertionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    private JwtDecoder jwtDecoder;

    private final DrsObjectRepository drsObjectRepository;

    private final DrsConfig drsConfig;

    private final PassportUserVisaAssertionService passportUserVisaAssertionService;

    public DrsObjectService(JwtDecoder jwtDecoder, DrsObjectRepository drsObjectRepository, DrsConfig drsConfig, PassportUserVisaAssertionService passportUserVisaAssertionService) {
        this.jwtDecoder = jwtDecoder;
        this.drsObjectRepository = drsObjectRepository;
        this.drsConfig = drsConfig;
        this.passportUserVisaAssertionService = passportUserVisaAssertionService;
    }

    @Transactional(readOnly = true)
    public DrsObjectResponseDTO getDrsObjectById(String id) {
        return convertToResponseDTO(loadDrsObject(id));
    }

    @Transactional(readOnly = true)
    public String getVisaIdByDrsObjectId(String id) {
        DrsObject drsObject = loadDrsObject(id);
        return drsObject.getDataset().getPassportVisa().getId();
    }

    public SingleDrsObjectAuthInfoResponseDTO getDrsObjectAuthInfo(String id) {
        requireDrsObjectExists(id);
        return generateAuthInfoForSingleDrsObjectId(id);
    }

    public MultiDrsObjectAuthInfoResponseDTO getMultiDrsObjectsAuthInfo(List<String> bulkObjectIds) {
        int requested = 0;
        int resolved = 0;
        int unresolved = 0;
        List<String> drsIdsResolved = new ArrayList<>();
        List<String> drsIdsUnresolvedNotFound = new ArrayList<>();

        for (String objectId : bulkObjectIds) {
            requested++;
            if (drsObjectRepository.existsById(objectId)) {
                resolved++;
                drsIdsResolved.add(objectId);
            } else {
                unresolved++;
                drsIdsUnresolvedNotFound.add(objectId);
            }
        }

        return new MultiDrsObjectAuthInfoResponseDTO(
            new MultiDrsObjectAuthInfoSummaryResponseDTO(requested, resolved, unresolved),
            List.of(
                new MultiDrsObjectAuthInfoUnresolvedIdSetResponseDTO(
                    HttpStatus.NOT_FOUND.value(),
                    drsIdsUnresolvedNotFound
                )
            ),
            drsIdsResolved.stream().map(objectId -> generateAuthInfoForSingleDrsObjectId(objectId)).collect(Collectors.toList())
        );
    }

    public MultiDrsObjectResponseDTO getMultiDrsObjects(List<String> passportTokens, List<String> bulkObjectIds) {
        int requested = 0;
        int resolved = 0;
        int unresolved = 0;
        List<DrsObjectResponseDTO> drsObjectsResolved = new ArrayList<>();
        List<String> drsIdsUnresolvedNotFound = new ArrayList<>();
        List<String> drsIdsUnresolvedForbidden = new ArrayList<>();

        List<Jwt> decodedPassports = passportTokens.stream().map(passportToken -> jwtDecoder.decode(passportToken)).collect(Collectors.toList());

        for (String objectId : bulkObjectIds) {
            requested++;

            if (drsObjectRepository.existsById(objectId)) { // first check if object exists, otherwise unresolved at Not Found
                boolean authorized = false;

                // next iterate through all passport tokens and check if user can access DrsObject, otherwise unresolved as Forbidden
                for (Jwt decodedPassport : decodedPassports) {
                    String userId = decodedPassport.getSubject();
                    if (validateUserIsAuthorizedForDrsObject(userId, objectId)) {
                        authorized = true;
                        break;
                    }
                }

                if (authorized == true) {
                    resolved++;
                    drsObjectsResolved.add(convertToResponseDTO(loadDrsObject(objectId)));
                } else { // unresolved - Forbidden
                    unresolved++;
                    drsIdsUnresolvedForbidden.add(objectId);
                }

            } else { // unresolved - Not Found
                unresolved++;
                drsIdsUnresolvedNotFound.add(objectId);
            }
        }

        return new MultiDrsObjectResponseDTO(
            new MultiDrsObjectAuthInfoSummaryResponseDTO(requested, resolved, unresolved),
            List.of(
                new MultiDrsObjectAuthInfoUnresolvedIdSetResponseDTO(
                    HttpStatus.NOT_FOUND.value(),
                    drsIdsUnresolvedNotFound
                ),
                new MultiDrsObjectAuthInfoUnresolvedIdSetResponseDTO(
                    HttpStatus.FORBIDDEN.value(),
                    drsIdsUnresolvedForbidden
                )
            ),
            drsObjectsResolved
        );
    }

    public boolean validateUserIsAuthorizedForDrsObject(String userId, String objectId) {
        String visaId = getVisaIdByDrsObjectId(objectId);

        Optional<PassportUserVisaAssertion> optionalAssertion = passportUserVisaAssertionService.getAssertionByUserIdAndVisaId(userId, visaId);
        if (optionalAssertion.isPresent()) {
            PassportUserVisaAssertion assertion = optionalAssertion.get();
            if (assertion.getCurrentStatus() == PassportVisaAssertionStatus.Approved) {
                return true; // if status is "Approved" allow user to view the object
            }
        }

        return false; // do not allow user to view the object if no record found in assertion table, or if status is anything other than "Approved"
    }

    public boolean bulkRequestWithinLimit(List<String> bulkObjectIds) {
        return bulkObjectIds.size() <= drsConfig.serviceInfo().drs().maxBulkLengthRequest();
    }

    @Transactional(readOnly = true)
    private Boolean requireDrsObjectExists(String id) {
        boolean exists = drsObjectRepository.existsById(id);
        if (exists == false) {
            throw new ResourceNotFoundException("No DRS Object with ID: " + id);
        }
        return exists;
    }

    @Transactional(readOnly = true)
    private DrsObject loadDrsObject(String id) {
        return drsObjectRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("No DRS Object with ID: " + id));
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

    private SingleDrsObjectAuthInfoResponseDTO generateAuthInfoForSingleDrsObjectId(String id) {
        return new SingleDrsObjectAuthInfoResponseDTO(
            id,
            List.of(SupportedType.BearerAuth, SupportedType.PassportAuth),
            List.of(issuerUri),
            List.of(issuerUri)
        );
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
