package org.ga4gh.refcloud.api.passport.passportuservisaassertion;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PassportUserVisaAssertionRepository extends JpaRepository<PassportUserVisaAssertion, PassportUserVisaAssertionId> {

    Optional<PassportUserVisaAssertion> findFirstByPassportUserIdAndPassportVisaId(String passportUserId, String passportVisaId);

    @Query("SELECT a FROM PassportUserVisaAssertion a WHERE a.passportUser.id = :userId")
    List<PassportUserVisaAssertion> findAllByUserId(@Param("userId") String userId);
}
