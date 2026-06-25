package org.ga4gh.refcloud.api.core.dataset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, String> {
    @Query("SELECT DISTINCT d FROM Dataset d LEFT JOIN FETCH d.tags")
    List<Dataset> findAllWithTags();

    @Query("SELECT d FROM Dataset d LEFT JOIN FETCH d.tags WHERE d.id = :id")
    Optional<Dataset> findByIdWithTags(@Param("id") String id);
}
