package org.ga4gh.refcloud.api.compute.run;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// finds the active run a run-scoped token belongs to. a token is valid only while its run is active, and the hash is
// cleared when the run ends
@Service
@Transactional(readOnly = true)
public class RunTokenService {

    private final ComputeRunRepository computeRunRepository;

    public RunTokenService(ComputeRunRepository computeRunRepository) {
        this.computeRunRepository = computeRunRepository;
    }

    public Optional<RunPrincipal> findActiveRun(String token) {
        if (!RunTokens.looksLikeRunToken(token)) {
            return Optional.empty();
        }
        return computeRunRepository
                .findByTokenHashAndStateIn(RunTokens.hash(token), ComputeRunStates.ACTIVE)
                .map(run -> new RunPrincipal(
                        run.getId(), run.getUserId(), run.getComputePlatformId(), run.getRunDirectory()));
    }
}
