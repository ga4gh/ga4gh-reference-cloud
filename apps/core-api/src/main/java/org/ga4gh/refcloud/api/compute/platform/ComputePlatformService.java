package org.ga4gh.refcloud.api.compute.platform;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputeConnections;
import org.ga4gh.refcloud.api.compute.health.ComputeHealthService;
import org.ga4gh.refcloud.api.compute.platformworkflow.ComputePlatformWorkflow;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.ga4gh.refcloud.api.passport.passportuser.PassportUser;
import org.ga4gh.refcloud.api.passport.passportuser.PassportUserRepository;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertion;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertionRepository;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertionResponseDTO;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportVisaAssertionStatus;
import org.ga4gh.refcloud.api.passport.passportvisa.PassportVisa;
import org.ga4gh.refcloud.api.passport.passportvisa.PassportVisaResponseDTO;
import org.ga4gh.refcloud.api.serviceregistry.ServiceRegistryConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// the researcher UI's view of compute platforms (Task 11), mirroring the datasets endpoints: list the enabled platforms
// with the caller's access request for each one's visa, and request access. disabled platforms aren't visible here,
// as everywhere outside the admin CLI (D10)
@Service
@Transactional(readOnly = true)
public class ComputePlatformService {

    private final ComputePlatformRepository computePlatformRepository;

    private final ComputeConnections computeConnections;

    private final ComputeHealthService computeHealthService;

    private final PassportUserRepository passportUserRepository;

    private final PassportUserVisaAssertionRepository passportUserVisaAssertionRepository;

    private final ServiceRegistryConfig serviceRegistryConfig;

    public ComputePlatformService(
            ComputePlatformRepository computePlatformRepository,
            ComputeConnections computeConnections,
            ComputeHealthService computeHealthService,
            PassportUserRepository passportUserRepository,
            PassportUserVisaAssertionRepository passportUserVisaAssertionRepository,
            ServiceRegistryConfig serviceRegistryConfig) {
        this.computePlatformRepository = computePlatformRepository;
        this.computeConnections = computeConnections;
        this.computeHealthService = computeHealthService;
        this.passportUserRepository = passportUserRepository;
        this.passportUserVisaAssertionRepository = passportUserVisaAssertionRepository;
        this.serviceRegistryConfig = serviceRegistryConfig;
    }

    public List<ComputePlatformResponseDTO> getEnabledPlatforms(String userId) {
        // the user's access requests, by visa id
        Map<String, PassportUserVisaAssertion> assertions =
                passportUserVisaAssertionRepository.findAllByUserId(userId).stream()
                        .filter(a -> a.getPassportVisa() != null)
                        .collect(Collectors.toMap(
                                a -> a.getPassportVisa().getId(), a -> a, (existing, duplicate) -> existing));
        return computePlatformRepository.findAllByEnabledTrueOrderByIdAsc().stream()
                .map(platform ->
                        toResponseDto(platform, assertions.get(visaOf(platform).getId())))
                .toList();
    }

    // creates a Requested access request for the platform's visa. a request that already exists is left as it is
    // (whatever its status) and returned, so asking twice is harmless
    @Transactional
    public ComputePlatformResponseDTO requestAccess(String userId, String platformId) {
        ComputePlatform platform = computePlatformRepository
                .findByIdAndEnabledTrue(platformId)
                .orElseThrow(() -> new ResourceNotFoundException("No compute platform with ID: " + platformId));
        PassportVisa visa = visaOf(platform);

        Optional<PassportUserVisaAssertion> existing =
                passportUserVisaAssertionRepository.findFirstByPassportUserIdAndPassportVisaId(userId, visa.getId());
        if (existing.isPresent()) {
            return toResponseDto(platform, existing.get());
        }

        PassportUser user = passportUserRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No passport user with ID: " + userId));
        PassportUserVisaAssertion assertion = new PassportUserVisaAssertion();
        assertion.setPassportVisa(visa);
        assertion.setPassportUser(user);
        assertion.setCurrentStatus(PassportVisaAssertionStatus.Requested);
        assertion.setCurrentStatusAt(Instant.now());
        passportUserVisaAssertionRepository.save(assertion);
        return toResponseDto(platform, assertion);
    }

    private ComputePlatformResponseDTO toResponseDto(ComputePlatform platform, PassportUserVisaAssertion assertion) {
        Optional<ComputeConfig.Connection> connection = computeConnections.find(platform.getConnectionKey());
        String base = serviceRegistryConfig.baseUrlWithoutTrailingSlash();
        PassportVisa visa = visaOf(platform);
        PassportUserVisaAssertionResponseDTO assertionDto = assertion == null
                ? null
                : new PassportUserVisaAssertionResponseDTO(
                        assertion.getPassportUser().getId(),
                        assertion.getCurrentStatus(),
                        assertion.getCurrentStatusAt());
        return new ComputePlatformResponseDTO(
                platform.getId(),
                platform.getName(),
                platform.getDescription(),
                connection.map(c -> c.type().key()).orElse(null),
                computeHealthService.healthOf(platform.getConnectionKey()),
                base + "/ga4gh/wes/" + platform.getId() + "/v1",
                base + "/ga4gh/tes/" + platform.getId() + "/v1",
                limits(platform, connection.map(ComputeConfig.Connection::limits)),
                platform.getWorkflows().stream()
                        // by name (never null), case-insensitively
                        .sorted(Comparator.comparing(ComputePlatformWorkflow::getName, String.CASE_INSENSITIVE_ORDER))
                        .map(workflow -> new ComputePlatformResponseDTO.Workflow(
                                workflow.getName(),
                                workflow.getDescription(),
                                workflow.getWorkflowUrl(),
                                workflow.getWorkflowType(),
                                workflow.getWorkflowTypeVersion()))
                        .toList(),
                new PassportVisaResponseDTO(visa.getId(), visa.getName(), visa.getDescription(), assertionDto));
    }

    // what one user may use (see ComputePlatformResponseDTO.Limits). without a configured connection there is no
    // ceiling to apply, so the per-user limits are shown as they are
    static ComputePlatformResponseDTO.Limits limits(ComputePlatform platform, Optional<ComputeConfig.Limits> ceiling) {
        return new ComputePlatformResponseDTO.Limits(
                platform.getUserMaxConcurrentRuns(),
                lower(
                        platform.getUserMaxTaskCpuCores(),
                        ceiling.map(ComputeConfig.Limits::maxTaskCpuCores).orElse(null)),
                lower(
                        platform.getUserMaxTaskMemoryGb(),
                        ceiling.map(ComputeConfig.Limits::maxTaskMemoryGb).orElse(null)),
                lower(
                        platform.getUserMaxTaskDiskGb(),
                        ceiling.map(ComputeConfig.Limits::maxTaskDiskGb).orElse(null)),
                lower(
                        platform.getUserMaxRunDurationSeconds(),
                        ceiling.map(ComputeConfig.Limits::maxRunDurationSeconds).orElse(null)),
                platform.getUserMonthlyCoreHours());
    }

    // the lower of two optional limits (null = no limit)
    static <T extends Comparable<T>> T lower(T perUser, T ceiling) {
        if (perUser == null) {
            return ceiling;
        }
        if (ceiling == null) {
            return perUser;
        }
        return perUser.compareTo(ceiling) <= 0 ? perUser : ceiling;
    }

    // every compute platform has exactly one visa (no open-access compute), enforced by the database
    private static PassportVisa visaOf(ComputePlatform platform) {
        PassportVisa visa = platform.getPassportVisa();
        if (visa == null) {
            throw new IllegalStateException("compute platform " + platform.getId() + " has no visa");
        }
        return visa;
    }
}
