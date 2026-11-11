package org.ga4gh.refcloud.api.compute.platformworkflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComputePlatformWorkflowRepository extends JpaRepository<ComputePlatformWorkflow, Long> {}
