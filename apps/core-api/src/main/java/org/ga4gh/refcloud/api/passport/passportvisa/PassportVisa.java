package org.ga4gh.refcloud.api.passport.passportvisa;

import java.util.HashSet;
import java.util.Set;
import org.ga4gh.refcloud.api.core.dataset.Dataset;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertion;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "passport_visa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassportVisa {

    @Id
    private String id;

    private String name;

    private String description;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "dataset_id", referencedColumnName = "id")
    private Dataset dataset;

    @OneToMany(mappedBy = "passportVisa", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<PassportUserVisaAssertion> passportUserVisaAssertions = new HashSet<>();
}
