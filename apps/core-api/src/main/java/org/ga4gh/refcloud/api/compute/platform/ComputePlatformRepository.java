package org.ga4gh.refcloud.api.compute.platform;

import java.util.List;
import java.util.Optional;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportVisaAssertionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ComputePlatformRepository extends JpaRepository<ComputePlatform, String> {

    // only enabled platforms are visible through the APIs and the service registry (Task 5)
    Optional<ComputePlatform> findByIdAndEnabledTrue(String id);

    List<ComputePlatform> findAllByEnabledTrueOrderByIdAsc();

    // whether the user's request for the platform's visa has this status
    @Query("""
            SELECT COUNT(a) > 0 FROM PassportUserVisaAssertion a
            WHERE a.passportUser.id = :userId
              AND a.passportVisa.computePlatform.id = :platformId
              AND a.currentStatus = :status
            """)
    boolean hasVisaAssertion(
            @Param("userId") String userId,
            @Param("platformId") String platformId,
            @Param("status") PassportVisaAssertionStatus status);
}
