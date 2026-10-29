package org.ga4gh.refcloud.api.passport.passportuser;

import java.util.HashSet;
import java.util.Set;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertion;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "passport_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassportUser {

    @Id
    private String id;

    @OneToMany(mappedBy = "passportUser", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<PassportUserVisaAssertion> passportUserVisaAssertions = new HashSet<>();
}
