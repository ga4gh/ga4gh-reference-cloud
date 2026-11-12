package org.ga4gh.refcloud.api.compute.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

// a workflow run (Task 7). the run directory holds everything about it on disk (workflow, inputs, work, outputs, logs,
// and the engine's pid and exit code files, which let core-api reattach after a restart)
@Entity
@Table(name = "compute_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComputeRun {

    @Id
    private String id;

    private String computePlatformId;

    private String userId;

    private String state;

    // why the run ended the way it did, when that isn't obvious from the state (e.g. a max-duration cancel)
    private String stateReason;

    private String runDirectory;

    // what the engine runs: a workflow directory or main script inside the run directory
    private String workflowPath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode workflowParams;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    @Builder.Default
    private List<String> engineProfiles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> engineEnv = new HashMap<>();

    // the WES run request as received (Task 8)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode request;

    private Integer maxDurationSeconds;

    // sha-256 of the run-scoped TES token, set while the run is active
    private String tokenHash;

    private Long enginePid;

    // the engine process's start time, so a reused pid is never mistaken for the run's engine
    private Instant engineStartedAt;

    private Integer exitCode;

    @Column(updatable = false)
    private Instant createdAt;

    // set by the update trigger
    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    private Instant startedAt;

    private Instant cancelRequestedAt;

    private Instant endedAt;

    // when the workflow and inputs were put in place (Task 8); only prepared runs are launched. null while preparing
    private Instant preparedAt;

    // the main script, when it isn't main.nf (nextflow -main-script)
    private String mainScript;

    // the allowlist entry the run was accepted with (null if the entry was deleted since)
    private Long computePlatformWorkflowId;

    // a snapshot of how to get the workflow (source kind, repository and commit, or bundled path)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode workflowSource;

    // the DRS inputs to stage: [{uri, object_id, name, size, url}]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode drsInputs;
}
