package org.ga4gh.refcloud.api.serviceregistry;

import java.util.List;
import java.util.Optional;
import org.ga4gh.refcloud.api.dataconnect.DataConnectConfig;
import org.ga4gh.refcloud.api.drs.DrsConfig;
import org.ga4gh.refcloud.api.refget.RefgetConfig;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfoType;
import org.springframework.stereotype.Service;

@Service
public class ServiceRegistryService {

    // base paths of each API, relative to the service registry's base URL. Spec paths (e.g. /service-info, or
    // refget's /sequence/service-info) are appended to these
    static final String DRS_PATH = "/ga4gh/drs/v1";
    static final String DATA_CONNECT_PATH = "/ga4gh/data-connect/v1";
    static final String REFGET_PATH = "/ga4gh/refget/v2";

    // the service registry itself is deliberately not listed
    private final List<ExternalService> services;

    public ServiceRegistryService(
            ServiceRegistryConfig serviceRegistryConfig,
            DrsConfig drsConfig,
            DataConnectConfig dataConnectConfig,
            RefgetConfig refgetConfig) {
        String baseUrl = serviceRegistryConfig.baseUrlWithoutTrailingSlash();
        this.services = List.of(
                new ExternalService(drsConfig.serviceInfo(), baseUrl + DRS_PATH),
                new ExternalService(dataConnectConfig.serviceInfo(), baseUrl + DATA_CONNECT_PATH),
                new ExternalService(refgetConfig.sequence().serviceInfo(), baseUrl + REFGET_PATH));
    }

    public List<ExternalService> getServices() {
        return services;
    }

    // looked up among the listed services by their configured service-info id (exact match)
    public Optional<ExternalService> findService(String id) {
        return services.stream()
                .filter(service -> service.serviceInfo().id().equals(id))
                .findFirst();
    }

    // distinct types of the listed services, in the same order as getServices()
    public List<ServiceInfoType> getServiceTypes() {
        return services.stream()
                .map(service -> service.serviceInfo().type())
                .distinct()
                .toList();
    }
}
