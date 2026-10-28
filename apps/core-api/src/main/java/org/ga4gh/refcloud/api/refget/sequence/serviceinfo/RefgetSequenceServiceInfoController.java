package org.ga4gh.refcloud.api.refget.sequence.serviceinfo;

import org.ga4gh.refcloud.api.refget.RefgetConfig;
import org.ga4gh.refcloud.api.refget.RefgetConfig.Sequence.ServiceInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ga4gh/refget/v2/sequence/service-info")
public class RefgetSequenceServiceInfoController {

    private final RefgetConfig refgetConfig;

    public RefgetSequenceServiceInfoController(RefgetConfig refgetConfig) {
        this.refgetConfig = refgetConfig;
    }

    @GetMapping
    public ResponseEntity<ServiceInfo> getServiceInfo() {
        return ResponseEntity.ok(refgetConfig.sequence().serviceInfo());
    }
}
