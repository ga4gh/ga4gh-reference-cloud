package org.ga4gh.refcloud.api.passport.passportuservisaassertion;

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
public class PassportUserVisaAssertionId implements Serializable {

    private String passportUserId;
    private String passportVisaId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PassportUserVisaAssertionId that = (PassportUserVisaAssertionId) o;
        return Objects.equals(passportUserId, that.passportUserId) && 
               Objects.equals(passportVisaId, that.passportVisaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(passportUserId, passportVisaId);
    }
}
