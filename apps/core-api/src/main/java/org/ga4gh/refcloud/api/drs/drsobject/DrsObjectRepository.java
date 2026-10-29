package org.ga4gh.refcloud.api.drs.drsobject;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DrsObjectRepository extends JpaRepository<DrsObject, String> {

    @Query("SELECT d FROM DrsObject d WHERE d.dataset.id = :datasetId AND d.isManifest = true")
    Page<DrsObject> findDrsObjectManifestsByDatasetId(@Param("datasetId") String datasetId, Pageable pageable);

}
