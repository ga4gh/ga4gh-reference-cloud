package org.ga4gh.refcloud.api.compute.platform;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.math.BigDecimal;
import java.util.Optional;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.testng.annotations.Test;

public class ComputePlatformServiceTest {

    // the local connection's ceilings (deploy/local/compute/connections.yml)
    private static final ComputeConfig.Limits CEILING = new ComputeConfig.Limits(
            4,
            8,
            8,
            new BigDecimal("8"),
            4,
            new BigDecimal("4"),
            new BigDecimal("20"),
            3600,
            null,
            1,
            BigDecimal.ONE,
            BigDecimal.TEN);

    @Test
    public void perUserLimitsBelowTheCeilingAreShown() {
        ComputePlatform platform = ComputePlatform.builder()
                .userMaxConcurrentRuns(2)
                .userMaxTaskCpuCores(2)
                .userMaxTaskMemoryGb(new BigDecimal("2"))
                .userMaxTaskDiskGb(new BigDecimal("10"))
                .userMaxRunDurationSeconds(1800)
                .userMonthlyCoreHours(BigDecimal.TEN)
                .build();

        assertEquals(
                ComputePlatformService.limits(platform, Optional.of(CEILING)),
                new ComputePlatformResponseDTO.Limits(
                        2, 2, new BigDecimal("2"), new BigDecimal("10"), 1800, BigDecimal.TEN));
    }

    @Test
    public void unsetPerUserLimitsFallBackToTheCeilingWhereOneApplies() {
        ComputePlatformResponseDTO.Limits limits =
                ComputePlatformService.limits(ComputePlatform.builder().build(), Optional.of(CEILING));

        // per-task resources and the run duration are capped by the connection
        assertEquals(limits.maxTaskCpuCores(), Integer.valueOf(4));
        assertEquals(limits.maxTaskMemoryGb(), new BigDecimal("4"));
        assertEquals(limits.maxTaskDiskGb(), new BigDecimal("20"));
        assertEquals(limits.maxRunDurationSeconds(), Integer.valueOf(3600));
        // concurrent runs and the monthly quota have no per-user value (the connection's limits are pooled)
        assertNull(limits.maxConcurrentRuns());
        assertNull(limits.monthlyCoreHours());
    }

    @Test
    public void withoutAConnectionThePerUserLimitsAreShownAsTheyAre() {
        ComputePlatform platform =
                ComputePlatform.builder().userMaxTaskCpuCores(16).build();

        ComputePlatformResponseDTO.Limits limits = ComputePlatformService.limits(platform, Optional.empty());

        assertEquals(limits.maxTaskCpuCores(), Integer.valueOf(16));
        assertNull(limits.maxTaskMemoryGb());
    }

    @Test
    public void lowerPicksTheSmallerLimitAndTreatsNullAsNone() {
        assertEquals(ComputePlatformService.lower(2, 4), Integer.valueOf(2));
        assertEquals(ComputePlatformService.lower(8, 4), Integer.valueOf(4));
        assertEquals(ComputePlatformService.lower(null, 4), Integer.valueOf(4));
        assertEquals(ComputePlatformService.lower(2, null), Integer.valueOf(2));
        assertNull(ComputePlatformService.lower((Integer) null, null));
        // compared by value, not scale
        assertEquals(ComputePlatformService.lower(new BigDecimal("2.0"), new BigDecimal("4")), new BigDecimal("2.0"));
    }
}
