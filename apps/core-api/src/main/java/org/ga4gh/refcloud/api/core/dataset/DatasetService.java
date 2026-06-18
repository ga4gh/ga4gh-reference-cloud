package org.ga4gh.refcloud.api.core.dataset;

import org.ga4gh.refcloud.api.core.tag.Tag;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertion;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertionRepository;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertionResponseDTO;
import org.ga4gh.refcloud.api.passport.passportvisa.PassportVisaResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;

    private final PassportUserVisaAssertionRepository passportUserVisaAssertionRepository;

    public DatasetService(DatasetRepository datasetRepository, PassportUserVisaAssertionRepository passportUserVisaAssertionRepository) {
        this.datasetRepository = datasetRepository;
        this.passportUserVisaAssertionRepository = passportUserVisaAssertionRepository;
    }

    @Transactional(readOnly = true)
    public List<DatasetResponseDTO> getAllDatasets(String userId) {
        // collect all datasets
        List<Dataset> datasets = datasetRepository.findAllWithTagsAndVisas();

        // collect all assertions for the user, then place in a map where the key is the visaId and value is the assertion object
        List<PassportUserVisaAssertion> assertions = passportUserVisaAssertionRepository.findAllByUserId(userId);
        Map<String, PassportUserVisaAssertion> assertionMap = assertions.stream()
            .filter(a -> a.getPassportVisa() != null && a.getPassportVisa().getId() != null)
            .collect(Collectors.toMap(
                assertion -> assertion.getPassportVisa().getId(), // Key: Visa ID
                assertion -> assertion,                           // Value: The assertion object
                (existing, replacement) -> existing              // Merge function: handles unexpected duplicates safely
            ));

        // prepare DTOs, affixing assertion information if it exists for the user
        List<DatasetResponseDTO> datasetDtos = new ArrayList<>();

        for (Dataset dataset : datasets) {
            PassportUserVisaAssertion assertion = null;
            if (assertionMap.containsKey(dataset.getPassportVisa().getId())) {
                assertion = assertionMap.get(dataset.getPassportVisa().getId());
            }
            datasetDtos.add(convertToResponseDto(dataset, assertion));
        }

        return datasetDtos;
    }

    @Transactional(readOnly = true)
    public DatasetResponseDTO getDatasetById(String userId, String datasetId) {
        Dataset dataset = datasetRepository.findByIdWithTagsAndVisas(datasetId).orElse(null);
        if (dataset == null) {
            return null;
        }

        String visaId = dataset.getPassportVisa().getId();
        PassportUserVisaAssertion assertion = null;
        Optional<PassportUserVisaAssertion> optionalAssertion = passportUserVisaAssertionRepository.findFirstByPassportUserIdAndPassportVisaId(userId, visaId);
        if (optionalAssertion.isPresent()) {
            assertion = optionalAssertion.get();
        }

        return convertToResponseDto(dataset, assertion);
    }

    private DatasetResponseDTO convertToResponseDto(Dataset dataset, PassportUserVisaAssertion assertion) {
        // prepare tags
        Set<String> tagDtos = dataset.getTags()
                .stream()
                .map(Tag::getTag)
                .collect(Collectors.toSet());

        // prepare assertion
        PassportUserVisaAssertionResponseDTO assertionDto = null;
        if (assertion != null) {
            assertionDto = new PassportUserVisaAssertionResponseDTO(
                assertion.getPassportUser().getId(),
                assertion.getCurrentStatus(),
                assertion.getCurrentStatusAt()
            );
        }

        // prepare visa
        PassportVisaResponseDTO visaDto = new PassportVisaResponseDTO(
            dataset.getPassportVisa().getId(),
            dataset.getPassportVisa().getName(),
            dataset.getPassportVisa().getDescription(),
            assertionDto
        );

        // prepare final dataset
        return new DatasetResponseDTO(
                dataset.getId(),
                dataset.getName(),
                dataset.getDescription(),
                tagDtos,
                visaDto
        );
    }
}