package org.ga4gh.refcloud.api.dataconnect.tabletype;

import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

// read-only: rows are managed externally by the CLI
@Entity
@Immutable
@Table(name = "data_connect_table_type")
@Getter
@NoArgsConstructor
public class DataConnectTableType {

    @Id
    private String id;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_model_template", columnDefinition = "jsonb")
    private JsonNode dataModelTemplate;

    private String internalQuery;

    // set by database default and update trigger
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
