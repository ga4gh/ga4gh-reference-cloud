package org.ga4gh.refcloud.api.compute.platform;

import java.util.List;
import org.ga4gh.refcloud.api.security.KratosSessionResponse.Identity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// compute platforms for the researcher UI (Task 11), mirroring /datasets: secured by the researcher's Kratos session
// (SecurityConfig's ORY_KRATOS_SESSION_ENDPOINTS), not a passport. not a GA4GH API
@RestController
@RequestMapping("/compute-platforms")
public class ComputePlatformController {

    private final ComputePlatformService computePlatformService;

    public ComputePlatformController(ComputePlatformService computePlatformService) {
        this.computePlatformService = computePlatformService;
    }

    @GetMapping
    public ResponseEntity<List<ComputePlatformResponseDTO>> getComputePlatforms(
            @AuthenticationPrincipal Identity identity) {
        return ResponseEntity.ok(computePlatformService.getEnabledPlatforms(identity.getId()));
    }

    // 404 for an unknown or disabled platform; a request that already exists is returned as it is
    @PostMapping("/{platformId}/request-access")
    public ResponseEntity<ComputePlatformResponseDTO> requestAccess(
            @AuthenticationPrincipal Identity identity, @PathVariable String platformId) {
        return ResponseEntity.ok(computePlatformService.requestAccess(identity.getId(), platformId));
    }
}
