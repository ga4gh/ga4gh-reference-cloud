package org.ga4gh.refcloud.api.drs.drsobject;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DrsObjectRepository extends JpaRepository<DrsObject, String> {

}
