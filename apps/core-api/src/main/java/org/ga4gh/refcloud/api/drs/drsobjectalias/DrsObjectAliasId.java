package org.ga4gh.refcloud.api.drs.drsobjectalias;

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
public class DrsObjectAliasId implements Serializable {

    private String drsObjectId;
    private String alias;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DrsObjectAliasId that = (DrsObjectAliasId) o;
        return Objects.equals(drsObjectId, that.drsObjectId) && Objects.equals(alias, that.alias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(drsObjectId, alias);
    }
}
