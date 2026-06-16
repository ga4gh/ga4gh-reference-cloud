package org.ga4gh.refcloud.api.passport.passportuservisaassertion;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PassportUserVisaAssertionRepository extends JpaRepository<PassportUserVisaAssertion, PassportUserVisaAssertionId> {

    Optional<PassportUserVisaAssertion> findFirstByPassportUserIdAndPassportVisaId(String passportUserId, String passportVisaId);
    
}
