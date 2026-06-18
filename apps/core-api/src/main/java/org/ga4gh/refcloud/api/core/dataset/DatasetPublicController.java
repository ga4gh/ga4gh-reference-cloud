package org.ga4gh.refcloud.api.core.dataset;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.ga4gh.refcloud.api.security.KratosSessionResponse.Identity;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/datasets")
public class DatasetPublicController {

    private final DatasetService datasetService;

    public DatasetPublicController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping
    public ResponseEntity<List<DatasetResponseDTO>> getAllDatasets(@AuthenticationPrincipal Identity identity) {
        List<DatasetResponseDTO> datasets = datasetService.getAllDatasets(identity.getId());
        return ResponseEntity.ok(datasets);
    }

    @GetMapping("/{datasetId}")
    public ResponseEntity<DatasetResponseDTO> getDatasetById(@AuthenticationPrincipal Identity identity, @PathVariable String datasetId) {
        DatasetResponseDTO dataset = datasetService.getDatasetById(identity.getId(), datasetId);
        return ResponseEntity.ok(dataset);
    }

    @PostMapping("/{datasetId}/request-access")
    public ResponseEntity<DatasetResponseDTO> requestAccessToDatasetById(@AuthenticationPrincipal Identity identity, @PathVariable String datasetId) {
        DatasetResponseDTO dataset = datasetService.requestAccessToDatasetById(identity.getId(), datasetId);
        return ResponseEntity.ok(dataset);
    }
}
