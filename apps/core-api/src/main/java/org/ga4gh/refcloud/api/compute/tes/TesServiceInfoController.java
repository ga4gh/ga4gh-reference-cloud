package org.ga4gh.refcloud.api.compute.tes;

import org.ga4gh.refcloud.api.compute.serviceinfo.ComputeServiceInfoService;
import org.ga4gh.refcloud.api.compute.serviceinfo.TesServiceInfo;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// TES 1.1 service-info for one compute platform. public, like the other services' service-info, and served even
// when the platform is unhealthy, since it reports the health
@RestController
@RequestMapping("/ga4gh/tes/{platformId}/v1")
public class TesServiceInfoController {

    private final ComputeServiceInfoService computeServiceInfoService;

    public TesServiceInfoController(ComputeServiceInfoService computeServiceInfoService) {
        this.computeServiceInfoService = computeServiceInfoService;
    }

    @GetMapping("/service-info")
    public ResponseEntity<TesServiceInfo> getServiceInfo(@PathVariable String platformId) {
        return computeServiceInfoService
                .findTesServiceInfo(platformId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("no compute platform with ID '" + platformId + "'"));
    }
}
