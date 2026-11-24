package org.ga4gh.refcloud.api.compute;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// enables @Scheduled (connection health checks, ComputeHealthService)
@Configuration
@EnableScheduling
public class ComputeSchedulingConfig {}
