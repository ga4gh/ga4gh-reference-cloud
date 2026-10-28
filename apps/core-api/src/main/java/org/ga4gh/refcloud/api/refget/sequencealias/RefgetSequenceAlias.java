package org.ga4gh.refcloud.api.refget.sequencealias;

import org.ga4gh.refcloud.api.refget.sequence.RefgetSequence;

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
@Table(name = "refget_sequence_alias")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefgetSequenceAlias {

    @EmbeddedId
    @Builder.Default
    private RefgetSequenceAliasId id = new RefgetSequenceAliasId();

    @ManyToOne
    @MapsId("refgetSequenceId")
    @JoinColumn(name = "refget_sequence_id")
    private RefgetSequence refgetSequence;
}
