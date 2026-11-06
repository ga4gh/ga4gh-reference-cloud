package org.ga4gh.refcloud.api.drs.drsobject;

import java.util.List;
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

    @Query("SELECT DISTINCT d.dataset.id FROM DrsObject d WHERE d.isManifest = true")
    List<String> findDatasetIdsWithManifests();

    @Query(
            "SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM DrsObject d WHERE d.dataset.id = :datasetId AND d.isManifest = true")
    boolean existsManifestByDatasetId(@Param("datasetId") String datasetId);
}
