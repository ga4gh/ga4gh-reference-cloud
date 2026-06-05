package org.ga4gh.refcloud.api.drs.drsobjectchecksum;

import org.ga4gh.refcloud.api.drs.drsobject.DrsObject;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "drs_object_checksum")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrsObjectChecksum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String checksum;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type")
    private DrsChecksumType type;

    @ManyToOne
    @JoinColumn(name = "drs_object_id")
    private DrsObject drsObject;
}
