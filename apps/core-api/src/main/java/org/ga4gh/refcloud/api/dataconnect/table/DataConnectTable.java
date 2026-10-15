package org.ga4gh.refcloud.api.dataconnect.table;

import java.time.LocalDateTime;
import org.ga4gh.refcloud.api.core.dataset.Dataset;
import org.ga4gh.refcloud.api.dataconnect.tabletype.DataConnectTableType;
import org.hibernate.annotations.Immutable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

// read-only: rows are managed externally by the onboarding script and CLI
@Entity
@Immutable
@Table(name = "data_connect_table")
@Getter
@NoArgsConstructor
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
