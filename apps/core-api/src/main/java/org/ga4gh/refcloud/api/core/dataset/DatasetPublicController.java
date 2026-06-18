package org.ga4gh.refcloud.api.core.dataset;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.ga4gh.refcloud.api.security.KratosSessionResponse.Identity;


@RestController
@RequestMapping("/datasets")
public class DatasetPublicController {

    private final DatasetService datasetService;

    public DatasetPublicController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping
    public ResponseEntity<List<DatasetWithUserAssertionResponseDTO>> getAllDatasets(@AuthenticationPrincipal Identity identity) {
        List<DatasetWithUserAssertionResponseDTO> datasets = datasetService.getAllDatasetsWithUserAssertions(identity.getId());
        return ResponseEntity.ok(datasets);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DatasetResponseDTO> getDatasetById(@PathVariable String id) {
        DatasetResponseDTO dataset = datasetService.getDatasetById(id);
        return ResponseEntity.ok(dataset);
    }
}
