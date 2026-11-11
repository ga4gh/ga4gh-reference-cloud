package org.ga4gh.refcloud.api.dataconnect.tabletype;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

// read-only: rows are managed externally by the CLI
@Entity
@Immutable
@Table(name = "data_connect_table_type")
@Getter
@NoArgsConstructor
@SuppressFBWarnings(
        value = "UWF_UNWRITTEN_FIELD",
        justification = "read-only (@Immutable) entity: hibernate populates the fields by reflection")
public class DataConnectTableType {

    @Id
    private String id;

    private String description;

    // json (not jsonb) so properties keep their authored order
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_model_template", columnDefinition = "json")
    private JsonNode dataModelTemplate;

    private String internalQuery;

    // set by database default and update trigger
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Column(insertable = false, updatable = false)
    private Instant updatedAt;
}
