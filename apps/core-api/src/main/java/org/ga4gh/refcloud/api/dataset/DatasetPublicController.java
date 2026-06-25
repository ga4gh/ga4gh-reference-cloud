package org.ga4gh.refcloud.api.dataset;

import org.ga4gh.refcloud.api.dataset.DatasetResponseDTO;
import org.ga4gh.refcloud.api.dataset.DatasetService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/datasets")
public class DatasetPublicController {

    private final DatasetService datasetService;

    @Autowired
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
