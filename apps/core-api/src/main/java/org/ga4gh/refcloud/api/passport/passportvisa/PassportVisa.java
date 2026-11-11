package org.ga4gh.refcloud.api.passport.passportvisa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ga4gh.refcloud.api.compute.platform.ComputePlatform;
import org.ga4gh.refcloud.api.core.dataset.Dataset;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertion;

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

    // a visa gates exactly one resource: a dataset or a compute platform, never neither and never both (D7, enforced
    // by the passport_visa_gates_exactly_one_resource_check constraint). so exactly one of these is non-null
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "dataset_id", referencedColumnName = "id")
    private Dataset dataset;

    @OneToOne
    @JoinColumn(name = "compute_platform_id", referencedColumnName = "id")
    private ComputePlatform computePlatform;

    @OneToMany(mappedBy = "passportVisa", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<PassportUserVisaAssertion> passportUserVisaAssertions = new HashSet<>();
}
