package org.ga4gh.refcloud.api.compute.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

// a TES task the refcloud accepted (Task 6). reads are served from here, not live from the executor: executorTask
// is the latest task document from the executor (funnel), kept current by the reconciler. request is the task as
// accepted (with its effective resources), so a queued task can be forwarded later. executorTaskId stays internal;
// clients only ever see id
@Entity
@Table(name = "compute_task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComputeTask {

    @Id
    private String id;

    private String computePlatformId;

    private String userId;

    // the workflow run that created the task (Task 7)
    private String runId;

    private String state;

    private String name;

    // null until the task is forwarded to the executor
    private String executorTaskId;

    private Integer cpuCores;

    private BigDecimal ramGb;

    private BigDecimal diskGb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode request;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode executorTask;

    @Column(updatable = false)
    private Instant createdAt;

    // set by the update trigger
    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    private Instant forwardedAt;

    private Instant startedAt;

    private Instant endedAt;

    // cpu cores x wall-clock hours, set when the task finishes (quota accounting, D11)
    private BigDecimal coreHours;
}
