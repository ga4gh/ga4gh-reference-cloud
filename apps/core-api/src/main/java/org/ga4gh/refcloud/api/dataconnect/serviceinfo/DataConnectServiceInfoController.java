package org.ga4gh.refcloud.api.dataconnect.serviceinfo;

import org.ga4gh.refcloud.api.dataconnect.DataConnectConfig;
import org.ga4gh.refcloud.api.dataconnect.DataConnectConfig.ServiceInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ga4gh/data-connect/v1/service-info")
public class DataConnectServiceInfoController {

    private final DataConnectConfig dataConnectConfig;

    public DataConnectServiceInfoController(DataConnectConfig dataConnectConfig) {
        this.dataConnectConfig = dataConnectConfig;
    }

    @GetMapping
    public ResponseEntity<ServiceInfo> getServiceInfo() {
        return ResponseEntity.ok(dataConnectConfig.serviceInfo());
    }
}
