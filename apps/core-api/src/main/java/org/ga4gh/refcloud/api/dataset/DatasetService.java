package org.ga4gh.refcloud.api.dataset;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.ga4gh.refcloud.api.tag.Tag;

@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;

    @Autowired
    public DatasetService(DatasetRepository datasetRepository) {
        this.datasetRepository = datasetRepository;
    }

    // @Transactional
    // public DatasetResponseDTO saveDataset(DatasetRequestDTO dto) {
    //     Dataset dataset = new Dataset();
    //     dataset.setName(dto.name());
    //     dataset.setDescription(dto.description());
    //     dataset.setRowCount(dto.rowCount());

    //     Dataset saved = datasetRepository.save(dataset);
    //     return new DatasetResponseDTO(saved.getId(), saved.getName(), saved.getDescription(), saved.getRowCount());
    // }

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