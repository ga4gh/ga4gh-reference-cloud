package org.ga4gh.refcloud.api.core.dataset;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.ga4gh.refcloud.api.core.tag.Tag;
import org.ga4gh.refcloud.api.dataconnect.table.DataConnectTable;
import org.ga4gh.refcloud.api.dataconnect.table.DataConnectTableRepository;
import org.ga4gh.refcloud.api.drs.drsobject.DrsObject;
import org.ga4gh.refcloud.api.drs.drsobject.DrsObjectRepository;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.ga4gh.refcloud.api.passport.passportuser.PassportUser;
import org.ga4gh.refcloud.api.passport.passportuser.PassportUserRepository;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertion;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertionRepository;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertionResponseDTO;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertionService;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportVisaAssertionStatus;
import org.ga4gh.refcloud.api.passport.passportvisa.PassportVisa;
import org.ga4gh.refcloud.api.passport.passportvisa.PassportVisaResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;

    private final PassportUserVisaAssertionService passportUserVisaAssertionService;

    private final PassportUserRepository passportUserRepository;

    private final PassportUserVisaAssertionRepository passportUserVisaAssertionRepository;

    private final DrsObjectRepository drsObjectRepository;

    private final DataConnectTableRepository dataConnectTableRepository;

    public DatasetService(
            DatasetRepository datasetRepository,
            PassportUserVisaAssertionService passportUserVisaAssertionService,
            PassportUserRepository passportUserRepository,
            PassportUserVisaAssertionRepository passportUserVisaAssertionRepository,
            DrsObjectRepository drsObjectRepository,
            DataConnectTableRepository dataConnectTableRepository) {
        this.datasetRepository = datasetRepository;
        this.passportUserVisaAssertionService = passportUserVisaAssertionService;
        this.passportUserRepository = passportUserRepository;
        this.passportUserVisaAssertionRepository = passportUserVisaAssertionRepository;
        this.drsObjectRepository = drsObjectRepository;
        this.dataConnectTableRepository = dataConnectTableRepository;
    }

    @Transactional(readOnly = true)
    public List<DatasetResponseDTO> getAllDatasets(String userId) {
        // collect all datasets
        List<Dataset> datasets = datasetRepository.findAllWithTagsAndVisas();

        // collect all assertions for the user, then place in a map where the key is the visaId and value is the
        // assertion object
        List<PassportUserVisaAssertion> assertions = passportUserVisaAssertionRepository.findAllByUserId(userId);
        Map<String, PassportUserVisaAssertion> assertionMap = assertions.stream()
                .filter(a -> a.getPassportVisa() != null && a.getPassportVisa().getId() != null)
                .collect(Collectors.toMap(
                        assertion -> assertion.getPassportVisa().getId(), // Key: Visa ID
                        assertion -> assertion, // Value: The assertion object
                        (existing, replacement) -> existing // Merge function: handles unexpected duplicates safely
                        ));

        // collect the Data Connect tables visible to the user, grouped by dataset id.
        // platform-wide tables (no dataset) are skipped, as they belong to no dataset
        Map<String, List<DatasetTableResponseDTO>> tablesByDatasetId =
                dataConnectTableRepository.findAllVisibleToUser(userId).stream()
                        .filter(table -> table.getDataset() != null)
                        .collect(Collectors.groupingBy(
                                table -> table.getDataset().getId(),
                                Collectors.mapping(DatasetService::convertToTableDto, Collectors.toList())));

        // datasets with at least one manifest DRS object, regardless of the user's access
        Set<String> datasetIdsWithManifests = new HashSet<>(drsObjectRepository.findDatasetIdsWithManifests());

        // prepare DTOs, affixing assertion information if it exists for the user
        List<DatasetResponseDTO> datasetDtos = new ArrayList<>();

        for (Dataset dataset : datasets) {
            // open datasets have no visa, and so no assertion
            PassportUserVisaAssertion assertion = dataset.getPassportVisa() == null
                    ? null
                    : assertionMap.get(dataset.getPassportVisa().getId());
            datasetDtos.add(convertToResponseDto(
                    dataset,
                    assertion,
                    tablesByDatasetId.getOrDefault(dataset.getId(), List.of()),
                    datasetIdsWithManifests.contains(dataset.getId())));
        }

        return datasetDtos;
    }

    @Transactional(readOnly = true)
    public DatasetResponseDTO getDatasetById(String userId, String datasetId) {
        Dataset dataset = datasetRepository.findByIdWithTagsAndVisas(datasetId).orElse(null);
        if (dataset == null) {
            return null;
        }

        // open datasets have no visa, and so no assertion
        PassportUserVisaAssertion assertion = null;
        if (dataset.getPassportVisa() != null) {
            String visaId = dataset.getPassportVisa().getId();
            Optional<PassportUserVisaAssertion> optionalAssertion =
                    passportUserVisaAssertionRepository.findFirstByPassportUserIdAndPassportVisaId(userId, visaId);
            if (optionalAssertion.isPresent()) {
                assertion = optionalAssertion.get();
            }
        }

        List<DatasetTableResponseDTO> tables =
                dataConnectTableRepository.findAllVisibleToUserByDatasetId(datasetId, userId).stream()
                        .map(DatasetService::convertToTableDto)
                        .toList();

        return convertToResponseDto(
                dataset, assertion, tables, drsObjectRepository.existsManifestByDatasetId(datasetId));
    }

    // empty when the dataset is open (has no visa)
    @Transactional(readOnly = true)
    public Optional<String> getVisaIdByDatasetId(String id) {
        Dataset dataset = loadDataset(id);
        return Optional.ofNullable(dataset.getPassportVisa()).map(PassportVisa::getId);
    }

    @Transactional
    public DatasetResponseDTO requestAccessToDatasetById(String userId, String datasetId) {
        // retrieve dataset & visa object from db
        Dataset dataset = datasetRepository.findByIdWithTagsAndVisas(datasetId).orElse(null);
        if (dataset == null) {
            return null;
        }

        // open datasets have no visa to request
        if (dataset.getPassportVisa() == null) {
            throw new BadRequestException(
                    "Dataset with ID: " + datasetId + " is open access and does not require an access request");
        }

        // retrieve user object from db
        Optional<PassportUser> user = passportUserRepository.findById(userId);
        if (user.isEmpty()) {
            return null;
        }

        // check if an assertion object alredy exists. If it does, do not attempt to save to db
        Optional<PassportUserVisaAssertion> existingAssertion =
                passportUserVisaAssertionRepository.findFirstByPassportUserIdAndPassportVisaId(
                        userId, dataset.getPassportVisa().getId());
        if (existingAssertion.isPresent()) {
            return null;
        }

        PassportUserVisaAssertion assertion = new PassportUserVisaAssertion();
        assertion.setPassportVisa(dataset.getPassportVisa());
        assertion.setPassportUser(user.get());
        assertion.setCurrentStatus(PassportVisaAssertionStatus.Requested);
        assertion.setCurrentStatusAt(Instant.now());
        passportUserVisaAssertionRepository.save(assertion);

        // tables and hasManifests are only returned by the read endpoints
        return convertToResponseDto(dataset, assertion, null, null);
    }

    @Transactional
    public Page<ManifestResponseDTO> getDrsObjectManifestsForDataset(
            String datasetId, int page, int size, String sortBy, String direction) {
        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<DrsObject> drsObjectsPage = drsObjectRepository.findDrsObjectManifestsByDatasetId(datasetId, pageable);
        Page<ManifestResponseDTO> manifestDtoPage = drsObjectsPage.map(drsObject -> new ManifestResponseDTO(
                drsObject.getId(),
                drsObject.getName(),
                drsObject.getSize(),
                drsObject.getCreatedTime(),
                drsObject.getUpdatedTime(),
                drsObject.getVersion(),
                drsObject.getMimeType(),
                drsObject.getDescription(),
                drsObject.getIsManifest(),
                drsObject.getManifestContent()));
        return manifestDtoPage;
    }

    // userId is null for anonymous requests, which may only access open datasets
    public boolean validateUserIsAuthorizedForDataset(String userId, String datasetId) {
        Optional<String> visaId = getVisaIdByDatasetId(datasetId);
        if (visaId.isEmpty()) {
            return true; // open dataset, no visa required
        }

        if (userId == null) {
            return false;
        }

        Optional<PassportUserVisaAssertion> optionalAssertion =
                passportUserVisaAssertionService.getAssertionByUserIdAndVisaId(userId, visaId.get());
        if (optionalAssertion.isPresent()) {
            PassportUserVisaAssertion assertion = optionalAssertion.get();
            if (assertion.getCurrentStatus() == PassportVisaAssertionStatus.Approved) {
                return true; // if status is "Approved" allow user to view the object
            }
        }

        return false; // do not allow user to view the object if no record found in assertion table, or if status is
        // anything other than "Approved"
    }

    private Dataset loadDataset(String id) {
        return datasetRepository
                .findByIdWithTagsAndVisas(id)
                .orElseThrow(() -> new ResourceNotFoundException("No Dataset with ID: " + id));
    }

    private static DatasetTableResponseDTO convertToTableDto(DataConnectTable table) {
        return new DatasetTableResponseDTO(
                table.getTableName(), table.getTableType().getId());
    }

    // tables and hasManifests are null when the caller should not return them
    private DatasetResponseDTO convertToResponseDto(
            Dataset dataset,
            PassportUserVisaAssertion assertion,
            List<DatasetTableResponseDTO> tables,
            Boolean hasManifests) {
        // prepare tags
        Set<String> tagDtos = dataset.getTags().stream().map(Tag::getTag).collect(Collectors.toSet());

        // prepare assertion
        PassportUserVisaAssertionResponseDTO assertionDto = null;
        if (assertion != null) {
            assertionDto = new PassportUserVisaAssertionResponseDTO(
                    assertion.getPassportUser().getId(), assertion.getCurrentStatus(), assertion.getCurrentStatusAt());
        }

        // prepare visa (null for open datasets)
        PassportVisaResponseDTO visaDto = null;
        if (dataset.getPassportVisa() != null) {
            visaDto = new PassportVisaResponseDTO(
                    dataset.getPassportVisa().getId(),
                    dataset.getPassportVisa().getName(),
                    dataset.getPassportVisa().getDescription(),
                    assertionDto);
        }

        // prepare final dataset
        return new DatasetResponseDTO(
                dataset.getId(), dataset.getName(), dataset.getDescription(), tagDtos, visaDto, tables, hasManifests);
    }
}
