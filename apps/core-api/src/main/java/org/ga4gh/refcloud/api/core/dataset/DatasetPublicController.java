package org.ga4gh.refcloud.api.core.dataset;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import org.ga4gh.refcloud.api.drs.drsobject.DrsObject;
import org.ga4gh.refcloud.api.security.KratosSessionResponse.Identity;

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

    @GetMapping("/{datasetId}/manifests")
    @PreAuthorize("@GA4GHPassportTokenEvaluator.canAccessDataset(authentication, #datasetId)")
    public ResponseEntity<Page<ManifestResponseDTO>> getManifestsForDataset(
        @PathVariable String datasetId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "id") String sortBy,
        @RequestParam(defaultValue = "asc") String direction
    ) {
        Page<ManifestResponseDTO> manifestDtoPage = datasetService.getDrsObjectManifestsForDataset(datasetId, page, size, sortBy, direction);
        return ResponseEntity.ok(manifestDtoPage);
    }
}
