package org.ga4gh.refcloud.api.refget.sequence;

import java.util.HashSet;
import java.util.Set;
import org.ga4gh.refcloud.api.drs.drsobject.DrsObject;
import org.ga4gh.refcloud.api.refget.sequencealias.RefgetSequenceAlias;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refget_sequence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefgetSequence {

    @Id
    private String drsObjectId;

    @Builder.Default
    private Boolean isCircular = false;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "drs_object_id")
    private DrsObject drsObject;

    @OneToMany(mappedBy = "refgetSequence", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private Set<RefgetSequenceAlias> aliases = new HashSet<>();
}
