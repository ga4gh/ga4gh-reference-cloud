package org.ga4gh.refcloud.api.compute.platformworkflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
import org.ga4gh.refcloud.api.compute.platform.ComputePlatform;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// one entry in a compute platform's workflow allowlist (D20). a WES submission's workflow_url must match workflowUrl
// exactly. which source fields are set depends on sourceKind (enforced by a check constraint): git and trs entries
// have the git fields (trs entries are resolved to git when allowlisted), trs entries also have the trs fields, and
// bundled entries have only bundledPath. engine settings (D21) are set by the admin; researchers can't change them
@Entity
@Table(name = "compute_platform_workflow")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComputePlatformWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "compute_platform_id")
    private ComputePlatform computePlatform;

    private String workflowUrl;

    private String name;

    private String description;

    // WES workflow_type and workflow_type_version, e.g. NFL and DSL2
    private String workflowType;

    private String workflowTypeVersion;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source_kind")
    private ComputeWorkflowSourceKind sourceKind;

    private String gitRepositoryUrl;

    // full 40-character commit sha
    private String gitCommit;

    private String gitMainScript;

    private String trsServerUrl;

    private String trsToolId;

    private String trsVersionId;

    private String bundledPath;

    // engine profiles, e.g. ["test"] for nextflow's -profile test
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "engine_profiles", columnDefinition = "text[]")
    @Builder.Default
    private List<String> engineProfiles = new ArrayList<>();

    // engine environment variables, e.g. {"NXF_SYNTAX_PARSER": "v1"}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "engine_env", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> engineEnv = new HashMap<>();

    // set by database default and update trigger
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Column(insertable = false, updatable = false)
    private Instant updatedAt;
}
