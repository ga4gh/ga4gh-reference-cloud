package org.ga4gh.refcloud.api.passport.passportuservisaassertion;

import org.ga4gh.refcloud.api.passport.passportuser.PassportUser;
import org.ga4gh.refcloud.api.passport.passportvisa.PassportVisa;

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
@Table(name = "passport_user_visa_assertion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassportUserVisaAssertion {

    @EmbeddedId
    @Builder.Default
    private PassportUserVisaAssertionId id = new PassportUserVisaAssertionId();

    @ManyToOne
    @MapsId("passportUserId")
    @JoinColumn(name = "user_id")
    private PassportUser passportUser;

    @ManyToOne
    @MapsId("passportVisaId")
    @JoinColumn(name = "visa_id")
    private PassportVisa passportVisa;
}
