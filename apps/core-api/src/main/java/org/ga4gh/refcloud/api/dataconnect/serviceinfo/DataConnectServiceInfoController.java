package org.ga4gh.refcloud.api.dataconnect.serviceinfo;

import org.ga4gh.refcloud.api.dataconnect.DataConnectConfig;
import org.ga4gh.refcloud.api.serviceinfo.StandardServiceInfo;
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
    public ResponseEntity<StandardServiceInfo> getServiceInfo() {
        return ResponseEntity.ok(dataConnectConfig.serviceInfo());
    }
}
