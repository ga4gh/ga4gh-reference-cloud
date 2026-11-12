package org.ga4gh.refcloud.api.compute.platform;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComputePlatformRepository extends JpaRepository<ComputePlatform, String> {

    // only enabled platforms are visible through the APIs and the service registry (Task 5)
    Optional<ComputePlatform> findByIdAndEnabledTrue(String id);

    List<ComputePlatform> findAllByEnabledTrueOrderByIdAsc();
}
