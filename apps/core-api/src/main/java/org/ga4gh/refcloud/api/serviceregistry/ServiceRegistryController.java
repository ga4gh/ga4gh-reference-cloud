package org.ga4gh.refcloud.api.serviceregistry;

import java.util.List;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfoType;
import org.ga4gh.refcloud.api.serviceinfo.StandardServiceInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ga4gh/service-registry/v1")
public class ServiceRegistryController {

    private final ServiceRegistryConfig serviceRegistryConfig;

    private final ServiceRegistryService serviceRegistryService;

    public ServiceRegistryController(
            ServiceRegistryConfig serviceRegistryConfig, ServiceRegistryService serviceRegistryService) {
        this.serviceRegistryConfig = serviceRegistryConfig;
        this.serviceRegistryService = serviceRegistryService;
    }

    @GetMapping("/service-info")
    public ResponseEntity<StandardServiceInfo> getServiceInfo() {
        return ResponseEntity.ok(serviceRegistryConfig.serviceInfo());
    }

    @GetMapping("/services")
    public ResponseEntity<List<ExternalService>> getServices() {
        return ResponseEntity.ok(serviceRegistryService.getServices());
    }

    // literal path segments take precedence over path variables, so /services/types never matches here
    @GetMapping("/services/{serviceId}")
    public ResponseEntity<ExternalService> getServiceById(@PathVariable String serviceId) {
        return serviceRegistryService
                .findService(serviceId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("no service with ID '" + serviceId + "'"));
    }

    @GetMapping("/services/types")
    public ResponseEntity<List<ServiceInfoType>> getServiceTypes() {
        return ResponseEntity.ok(serviceRegistryService.getServiceTypes());
    }
}
