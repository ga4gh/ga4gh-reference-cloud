package org.ga4gh.refcloud.api.dataconnect.table;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ga4gh.refcloud.api.core.dataset.Dataset;
import org.ga4gh.refcloud.api.dataconnect.tabletype.DataConnectTableType;
import org.hibernate.annotations.Immutable;

// read-only: rows are managed externally by the onboarding script and CLI
@Entity
@Immutable
@Table(name = "data_connect_table")
@Getter
@NoArgsConstructor
@SuppressFBWarnings(
        value = "UWF_UNWRITTEN_FIELD",
        justification = "read-only (@Immutable) entity: hibernate populates the fields by reflection")
public class DataConnectTable {

    @Id
    private String tableName;

    // null for platform-wide tables (e.g. datasets) that do not belong to a dataset
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_type", nullable = false)
    private DataConnectTableType tableType;

    private String description;

    // set by database default and update trigger
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
