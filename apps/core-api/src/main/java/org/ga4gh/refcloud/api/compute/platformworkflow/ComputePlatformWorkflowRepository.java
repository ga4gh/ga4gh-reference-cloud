package org.ga4gh.refcloud.api.compute.platformworkflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ComputePlatformWorkflowRepository extends JpaRepository<ComputePlatformWorkflow, Long> {

    // the workflow types a platform's allowlist covers (WES service-info workflow_type_versions)
    @Query("""
            SELECT DISTINCT new org.ga4gh.refcloud.api.compute.platformworkflow.WorkflowTypeAndVersion(
                w.workflowType, w.workflowTypeVersion)
            FROM ComputePlatformWorkflow w
            WHERE w.computePlatform.id = :platformId
            """)
    List<WorkflowTypeAndVersion> findWorkflowTypes(@Param("platformId") String platformId);
}
