package org.ga4gh.refcloud.api.compute.serviceinfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputeConnections;
import org.ga4gh.refcloud.api.compute.ComputeServiceInfoConfig;
import org.ga4gh.refcloud.api.compute.health.ComputeHealth;
import org.ga4gh.refcloud.api.compute.health.ComputeHealthService;
import org.ga4gh.refcloud.api.compute.platform.ComputePlatform;
import org.ga4gh.refcloud.api.compute.platform.ComputePlatformRepository;
import org.ga4gh.refcloud.api.compute.platformworkflow.ComputePlatformWorkflowRepository;
import org.ga4gh.refcloud.api.compute.platformworkflow.WorkflowTypeAndVersion;
import org.ga4gh.refcloud.api.compute.run.ComputeRunRepository;
import org.ga4gh.refcloud.api.compute.run.ComputeRunStates;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfoType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// builds each enabled compute platform's WES and TES service-info (D6, D10). only enabled platforms are visible:
// a disabled one is reported as not found everywhere except the admin CLI
@Service
@Transactional(readOnly = true)
public class ComputeServiceInfoService {

    public static final String WES = "wes";

    public static final String TES = "tes";

    static final ServiceInfoType WES_TYPE = new ServiceInfoType("org.ga4gh", WES, "1.1.0");

    static final ServiceInfoType TES_TYPE = new ServiceInfoType("org.ga4gh", TES, "1.1.0");

    private final ComputePlatformRepository computePlatformRepository;

    private final ComputePlatformWorkflowRepository computePlatformWorkflowRepository;

    private final ComputeConnections computeConnections;

    private final ComputeHealthService computeHealthService;

    private final ComputeServiceInfoConfig serviceInfoConfig;

    private final ComputeRunRepository computeRunRepository;

    public ComputeServiceInfoService(
            ComputePlatformRepository computePlatformRepository,
            ComputePlatformWorkflowRepository computePlatformWorkflowRepository,
            ComputeConnections computeConnections,
            ComputeHealthService computeHealthService,
            ComputeServiceInfoConfig serviceInfoConfig,
            ComputeRunRepository computeRunRepository) {
        this.computePlatformRepository = computePlatformRepository;
        this.computePlatformWorkflowRepository = computePlatformWorkflowRepository;
        this.computeConnections = computeConnections;
        this.computeHealthService = computeHealthService;
        this.serviceInfoConfig = serviceInfoConfig;
        this.computeRunRepository = computeRunRepository;
    }

    // any platform, enabled or not: an active run keeps working on a platform disabled after it started (Task 7)
    public Optional<ComputePlatform> findPlatform(String platformId) {
        return computePlatformRepository.findById(platformId);
    }

    public Optional<ComputePlatform> findEnabledPlatform(String platformId) {
        return computePlatformRepository.findByIdAndEnabledTrue(platformId);
    }

    public List<ComputePlatform> findEnabledPlatforms() {
        return computePlatformRepository.findAllByEnabledTrueOrderByIdAsc();
    }

    public ComputeHealth health(ComputePlatform platform) {
        return computeHealthService.healthOf(platform.getConnectionKey());
    }

    public Optional<WesServiceInfo> findWesServiceInfo(String platformId) {
        return findEnabledPlatform(platformId).map(this::wesServiceInfo);
    }

    public Optional<TesServiceInfo> findTesServiceInfo(String platformId) {
        return findEnabledPlatform(platformId).map(this::tesServiceInfo);
    }

    public WesServiceInfo wesServiceInfo(ComputePlatform platform) {
        Optional<ComputeConfig.Connection> connection = computeConnections.find(platform.getConnectionKey());

        // the workflow types the allowlist covers, e.g. {"NFL": {"workflow_type_version": ["DSL2"]}}
        Map<String, WesServiceInfo.WorkflowTypeVersion> workflowTypeVersions =
                computePlatformWorkflowRepository.findWorkflowTypes(platform.getId()).stream()
                        .collect(Collectors.groupingBy(
                                WorkflowTypeAndVersion::workflowType,
                                TreeMap::new,
                                Collectors.collectingAndThen(
                                        Collectors.mapping(
                                                WorkflowTypeAndVersion::workflowTypeVersion,
                                                Collectors.toCollection(TreeSet::new)),
                                        versions -> new WesServiceInfo.WorkflowTypeVersion(List.copyOf(versions)))));

        // e.g. {"nextflow": {"workflow_engine_version": ["26.10.5"]}}; empty if the connection is missing
        Map<String, WesServiceInfo.WorkflowEngineVersion> workflowEngineVersions = connection
                .map(c -> Map.of(
                        c.type().engine(),
                        new WesServiceInfo.WorkflowEngineVersion(
                                List.of(c.engine().nextflowVersion()))))
                .orElse(Map.of());

        Map<String, String> tags =
                connection.map(c -> Map.of("platform_type", c.type().key())).orElse(Map.of());

        return new WesServiceInfo(
                serviceInfoConfig.serviceId(WES, platform.getId()),
                platform.getName() + " (WES)",
                WES_TYPE,
                platform.getDescription(),
                serviceInfoConfig.organization(),
                serviceInfoConfig.contactUrl(),
                serviceInfoConfig.documentationUrl(),
                platform.getCreatedAt(),
                platform.getUpdatedAt(),
                serviceInfoConfig.environment(),
                serviceInfoConfig.version(),
                workflowTypeVersions,
                List.of("1.1.0"),
                // inputs are drs:// only (D17)
                List.of("drs"),
                workflowEngineVersions,
                // workflow_engine_parameters are rejected (D21), so there are no defaults to report
                List.of(),
                systemStateCounts(platform.getId()),
                serviceInfoConfig.authInstructionsUrl(),
                tags,
                health(platform));
    }

    // every WES state with the platform's run count in it (zero included), in a stable order
    private Map<String, Long> systemStateCounts(String platformId) {
        Map<String, Long> counts = new TreeMap<>();
        for (String state : ComputeRunStates.ALL) {
            counts.put(state, 0L);
        }
        for (Object[] row : computeRunRepository.countByState(platformId)) {
            counts.put((String) row[0], (Long) row[1]);
        }
        return counts;
    }

    public TesServiceInfo tesServiceInfo(ComputePlatform platform) {
        return new TesServiceInfo(
                serviceInfoConfig.serviceId(TES, platform.getId()),
                platform.getName() + " (TES)",
                TES_TYPE,
                platform.getDescription(),
                serviceInfoConfig.organization(),
                serviceInfoConfig.contactUrl(),
                serviceInfoConfig.documentationUrl(),
                platform.getCreatedAt(),
                platform.getUpdatedAt(),
                serviceInfoConfig.environment(),
                serviceInfoConfig.version(),
                List.of(),
                health(platform));
    }
}
