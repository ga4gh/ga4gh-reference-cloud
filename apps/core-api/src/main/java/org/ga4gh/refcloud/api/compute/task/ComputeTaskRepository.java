package org.ga4gh.refcloud.api.compute.task;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ComputeTaskRepository extends JpaRepository<ComputeTask, String> {

    Optional<ComputeTask> findByIdAndComputePlatformId(String id, String computePlatformId);

    // a run's tasks that haven't finished
    List<ComputeTask> findByRunIdAndStateNotIn(String runId, Collection<String> states);

    // tasks forwarded to the executor and not finished, on every platform backed by a connection (pooled capacity)
    @Query("""
            SELECT t FROM ComputeTask t, ComputePlatform p
            WHERE t.computePlatformId = p.id AND p.connectionKey = :connectionKey
              AND t.executorTaskId IS NOT NULL AND t.state NOT IN :terminalStates
            """)
    List<ComputeTask> findInFlight(
            @Param("connectionKey") String connectionKey, @Param("terminalStates") Collection<String> terminalStates);

    // tasks waiting to be forwarded, oldest first
    @Query("""
            SELECT t FROM ComputeTask t, ComputePlatform p
            WHERE t.computePlatformId = p.id AND p.connectionKey = :connectionKey
              AND t.executorTaskId IS NULL AND t.state = 'QUEUED'
            ORDER BY t.createdAt, t.id
            """)
    List<ComputeTask> findWaiting(@Param("connectionKey") String connectionKey);

    // GET /tasks: the caller's tasks on a platform, newest first, after the keyset (afterCreatedAt, afterId).
    // ownership is by user (a researcher) or by run (a run token); filters are optional (one tag pair). native, for the
    // jsonb tag filter (jsonb_exists rather than the ? operator, which jdbc reads as a parameter); casts let postgres
    // type the optional (possibly null) parameters
    @Query(value = """
                    SELECT * FROM compute_task t
                    WHERE t.compute_platform_id = :platformId
                      AND (CAST(:userId AS text) IS NULL OR t.user_id = :userId)
                      AND (CAST(:runId AS text) IS NULL OR t.run_id = :runId)
                      AND (CAST(:state AS text) IS NULL OR t.state = :state)
                      AND (CAST(:namePrefix AS text) IS NULL OR starts_with(t.name, :namePrefix))
                      AND (CAST(:tagKey AS text) IS NULL OR jsonb_exists(t.request -> 'tags', :tagKey))
                      AND (CAST(:tagValue AS text) IS NULL OR (t.request -> 'tags' ->> :tagKey) = :tagValue)
                      AND (CAST(:afterCreatedAt AS timestamptz) IS NULL
                           OR (t.created_at, t.id) < (CAST(:afterCreatedAt AS timestamptz), CAST(:afterId AS text)))
                    ORDER BY t.created_at DESC, t.id DESC
                    LIMIT :limit
                    """, nativeQuery = true)
    List<ComputeTask> findPage(
            @Param("platformId") String platformId,
            @Param("userId") String userId,
            @Param("runId") String runId,
            @Param("state") String state,
            @Param("namePrefix") String namePrefix,
            @Param("tagKey") String tagKey,
            @Param("tagValue") String tagValue,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") String afterId,
            @Param("limit") int limit);

    // a user's core-hours on a platform from tasks that finished at or after `since` (the monthly quota, D11)
    @Query("""
            SELECT COALESCE(SUM(t.coreHours), 0) FROM ComputeTask t
            WHERE t.computePlatformId = :platformId AND t.userId = :userId AND t.endedAt >= :since
            """)
    BigDecimal sumUserCoreHoursSince(
            @Param("platformId") String platformId, @Param("userId") String userId, @Param("since") Instant since);

    // everyone's core-hours on every platform backed by a connection, from tasks that finished at or after `since`
    // (the connection's pooled monthly budget, D11)
    @Query("""
            SELECT COALESCE(SUM(t.coreHours), 0) FROM ComputeTask t, ComputePlatform p
            WHERE t.computePlatformId = p.id AND p.connectionKey = :connectionKey AND t.endedAt >= :since
            """)
    BigDecimal sumConnectionCoreHoursSince(@Param("connectionKey") String connectionKey, @Param("since") Instant since);
}
