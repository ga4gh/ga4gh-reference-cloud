package org.ga4gh.refcloud.api.drs.drsobjectalias;

import org.ga4gh.refcloud.api.drs.drsobject.DrsObject;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
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
@Table(name = "drs_object_alias")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrsObjectAlias {

    @EmbeddedId
    @Builder.Default
    private DrsObjectAliasId id = new DrsObjectAliasId();

    @ManyToOne
    @MapsId("drsObjectId")
    @JoinColumn(name = "drs_object_id")
    private DrsObject drsObject;
}
