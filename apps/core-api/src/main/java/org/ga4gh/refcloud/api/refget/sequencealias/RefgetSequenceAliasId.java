package org.ga4gh.refcloud.api.refget.sequencealias;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefgetSequenceAliasId implements Serializable {

    private String refgetSequenceId;
    private String namingAuthority;
    private String alias;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RefgetSequenceAliasId that = (RefgetSequenceAliasId) o;
        return Objects.equals(refgetSequenceId, that.refgetSequenceId)
            && Objects.equals(namingAuthority, that.namingAuthority)
            && Objects.equals(alias, that.alias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(refgetSequenceId, namingAuthority, alias);
    }
}
