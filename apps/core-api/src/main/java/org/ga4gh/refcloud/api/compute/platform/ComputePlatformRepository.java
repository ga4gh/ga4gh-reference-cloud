package org.ga4gh.refcloud.api.compute.platform;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComputePlatformRepository extends JpaRepository<ComputePlatform, String> {}
