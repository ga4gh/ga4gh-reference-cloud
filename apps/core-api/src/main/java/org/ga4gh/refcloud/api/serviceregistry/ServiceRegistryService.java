package org.ga4gh.refcloud.api.serviceregistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ga4gh.refcloud.api.compute.platform.ComputePlatform;
import org.ga4gh.refcloud.api.compute.serviceinfo.ComputeServiceInfoService;
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

    private final String baseUrl;

    // the services configured at startup (DRS, Data Connect, refget). the service registry itself is deliberately
    // not listed
    private final List<ExternalService> staticServices;

    private final ComputeServiceInfoService computeServiceInfoService;

    public ServiceRegistryService(
            ServiceRegistryConfig serviceRegistryConfig,
            DrsConfig drsConfig,
            DataConnectConfig dataConnectConfig,
            RefgetConfig refgetConfig,
            ComputeServiceInfoService computeServiceInfoService) {
        this.baseUrl = serviceRegistryConfig.baseUrlWithoutTrailingSlash();
        this.staticServices = List.of(
                new ExternalService(drsConfig.serviceInfo(), baseUrl + DRS_PATH),
                new ExternalService(dataConnectConfig.serviceInfo(), baseUrl + DATA_CONNECT_PATH),
                new ExternalService(refgetConfig.sequence().serviceInfo(), baseUrl + REFGET_PATH));
        this.computeServiceInfoService = computeServiceInfoService;
    }

    // the static services, then a WES and a TES service for each enabled compute platform (D6), whatever its health.
    // platforms are read on every call, so a newly enabled, disabled or renamed platform shows up without a restart
    public List<ExternalService> getServices() {
        List<ExternalService> services = new ArrayList<>(staticServices);
        for (ComputePlatform platform : computeServiceInfoService.findEnabledPlatforms()) {
            services.add(new ExternalService(
                    computeServiceInfoService.wesServiceInfo(platform),
                    baseUrl + "/ga4gh/" + ComputeServiceInfoService.WES + "/" + platform.getId() + "/v1"));
            services.add(new ExternalService(
                    computeServiceInfoService.tesServiceInfo(platform),
                    baseUrl + "/ga4gh/" + ComputeServiceInfoService.TES + "/" + platform.getId() + "/v1"));
        }
        return services;
    }

    // looked up among the listed services by their service-info id (exact match)
    public Optional<ExternalService> findService(String id) {
        return getServices().stream()
                .filter(service -> service.serviceInfo().id().equals(id))
                .findFirst();
    }

    // distinct types of the listed services, in the same order as getServices()
    public List<ServiceInfoType> getServiceTypes() {
        return getServices().stream()
                .map(service -> service.serviceInfo().type())
                .distinct()
                .toList();
    }
}
