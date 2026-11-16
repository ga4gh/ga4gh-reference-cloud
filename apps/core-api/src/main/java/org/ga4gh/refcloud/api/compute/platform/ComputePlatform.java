package org.ga4gh.refcloud.api.compute.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ga4gh.refcloud.api.compute.platformworkflow.ComputePlatformWorkflow;
import org.ga4gh.refcloud.api.passport.passportvisa.PassportVisa;

// an admin-registered, visa-gated compute platform (D5, D9). registered by the admin CLI; core-api reads it.
// it points at a connection in the connections file by connectionKey. per-user limits are nullable: null means no
// per-user cap, so only the connection's pooled ceiling applies (D11)
@Entity
@Table(name = "compute_platform")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComputePlatform {

    @Id
    private String id;

    private String name;

    private String description;

    private String connectionKey;

    private boolean enabled;

    private Integer userMaxConcurrentRuns;

    private Integer userMaxTaskCpuCores;

    private BigDecimal userMaxTaskMemoryGb;

    private BigDecimal userMaxTaskDiskGb;

    private Integer userMaxRunDurationSeconds;

    // monthly quota in core-hours (CPU cores x wall-clock hours, summed over the user's tasks)
    private BigDecimal userMonthlyCoreHours;

    // set by database default and update trigger
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    // every compute platform has exactly one visa (no open-access compute)
    @OneToOne(mappedBy = "computePlatform")
    private PassportVisa passportVisa;

    @OneToMany(mappedBy = "computePlatform")
    @Builder.Default
    private List<ComputePlatformWorkflow> workflows = new ArrayList<>();
}
