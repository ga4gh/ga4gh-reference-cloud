package org.ga4gh.refcloud.api.compute.run;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ComputeRunRepository extends JpaRepository<ComputeRun, String> {

    // the run a run-scoped token belongs to, if the run is active
    Optional<ComputeRun> findByTokenHashAndStateIn(String tokenHash, Collection<String> states);

    List<ComputeRun> findByStateAndPreparedAtIsNull(String state);

    // a user's runs on a platform in these states (the per-user concurrent-run limit)
    long countByComputePlatformIdAndUserIdAndStateIn(
            String computePlatformId, String userId, Collection<String> states);

    // run counts by state on a platform (WES service-info system_state_counts)
    @Query("SELECT r.state, COUNT(r) FROM ComputeRun r WHERE r.computePlatformId = :platformId GROUP BY r.state")
    List<Object[]> countByState(@Param("platformId") String platformId);

    // GET /runs: a user's runs on a platform, newest first, after the keyset (afterCreatedAt, afterId)
    @Query(value = """
                    SELECT * FROM compute_run r
                    WHERE r.compute_platform_id = :platformId AND r.user_id = :userId
                      AND (CAST(:afterCreatedAt AS timestamptz) IS NULL
                           OR (r.created_at, r.id) < (CAST(:afterCreatedAt AS timestamptz), CAST(:afterId AS text)))
                    ORDER BY r.created_at DESC, r.id DESC
                    LIMIT :limit
                    """, nativeQuery = true)
    List<ComputeRun> findPage(
            @Param("platformId") String platformId,
            @Param("userId") String userId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") String afterId,
            @Param("limit") int limit);

    // runs in these states on every platform backed by a connection
    @Query("""
            SELECT r FROM ComputeRun r, ComputePlatform p
            WHERE r.computePlatformId = p.id AND p.connectionKey = :connectionKey AND r.state IN :states
            ORDER BY r.createdAt, r.id
            """)
    List<ComputeRun> findByConnectionAndStates(
            @Param("connectionKey") String connectionKey, @Param("states") Collection<String> states);
}
