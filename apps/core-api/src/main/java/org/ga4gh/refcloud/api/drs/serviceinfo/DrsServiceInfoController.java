package org.ga4gh.refcloud.api.drs.serviceinfo;

import org.ga4gh.refcloud.api.drs.DrsConfig;
import org.ga4gh.refcloud.api.drs.DrsConfig.ServiceInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ga4gh/drs/v1/service-info")
public class DrsServiceInfoController {

    private final DrsConfig drsConfig;

    public DrsServiceInfoController(DrsConfig drsConfig) {
        this.drsConfig = drsConfig;
    }

    @GetMapping
    public ResponseEntity<ServiceInfo> getServiceInfo() {
        return ResponseEntity.ok(drsConfig.serviceInfo());
    }
}
