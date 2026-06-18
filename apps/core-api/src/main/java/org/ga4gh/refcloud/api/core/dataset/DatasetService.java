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
    public List<DatasetResponseDTO> getAllDatasets() {
        return datasetRepository.findAllWithTags()
                .stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DatasetResponseDTO getDatasetById(String id) {
        Dataset dataset = datasetRepository.findByIdWithTags(id).orElse(null);
        if (dataset == null) {
            return null;
        }
        return convertToResponseDto(dataset);
    }

    @Transactional(readOnly = true)
    public List<DatasetWithUserAssertionResponseDTO> getAllDatasetsWithUserAssertions(String userId) {
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
        List<DatasetWithUserAssertionResponseDTO> datasetDtos = new ArrayList<>();

        for (Dataset dataset : datasets) {
            Set<String> tagDtos = dataset.getTags()
                .stream()
                .map(Tag::getTag)
                .collect(Collectors.toSet());

            PassportUserVisaAssertionResponseDTO assertionDto = null;
            if (assertionMap.containsKey(dataset.getPassportVisa().getId())) {
                PassportUserVisaAssertion assertion = assertionMap.get(dataset.getPassportVisa().getId());
                assertionDto = new PassportUserVisaAssertionResponseDTO(
                    assertion.getPassportUser().getId(),
                    assertion.getCurrentStatus(),
                    assertion.getCurrentStatusAt()
                );
            }
            PassportVisaResponseDTO visaDto = new PassportVisaResponseDTO(
                dataset.getPassportVisa().getId(),
                dataset.getPassportVisa().getName(),
                dataset.getPassportVisa().getDescription(),
                assertionDto
            );

            DatasetWithUserAssertionResponseDTO dto = new DatasetWithUserAssertionResponseDTO(
                dataset.getId(),
                dataset.getName(),
                dataset.getDescription(),
                tagDtos,
                visaDto
            );
            datasetDtos.add(dto);
        }

        return datasetDtos;
    }

    private DatasetResponseDTO convertToResponseDto(Dataset dataset) {
        Set<String> tagDtos = dataset.getTags()
                .stream()
                .map(Tag::getTag)
                .collect(Collectors.toSet());

        return new DatasetResponseDTO(
                dataset.getId(),
                dataset.getName(),
                dataset.getDescription(),
                tagDtos
        );
    }
}