package org.ga4gh.refcloud.api.core.dataset;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/datasets")
public class DatasetPublicController {

    private final DatasetService datasetService;

    public DatasetPublicController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping
    public ResponseEntity<List<DatasetResponseDTO>> getAllDatasets() {
        List<DatasetResponseDTO> datasets = datasetService.getAllDatasets();
        return ResponseEntity.ok(datasets);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DatasetResponseDTO> getDatasetById(@PathVariable String id) {
        DatasetResponseDTO dataset = datasetService.getDatasetById(id);
        return ResponseEntity.ok(dataset);
    }
}
