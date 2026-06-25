package org.ga4gh.refcloud.api.passport.passportuser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PassportUserRepository extends JpaRepository<PassportUser, String> {

}
