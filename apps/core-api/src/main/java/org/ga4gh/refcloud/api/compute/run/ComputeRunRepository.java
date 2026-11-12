package org.ga4gh.refcloud.api.compute.run;

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

    // runs in these states on every platform backed by a connection
    @Query("""
            SELECT r FROM ComputeRun r, ComputePlatform p
            WHERE r.computePlatformId = p.id AND p.connectionKey = :connectionKey AND r.state IN :states
            ORDER BY r.createdAt, r.id
            """)
    List<ComputeRun> findByConnectionAndStates(
            @Param("connectionKey") String connectionKey, @Param("states") Collection<String> states);
}
