package org.ga4gh.refcloud.api.drs.drsobject;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.ga4gh.refcloud.api.core.dataset.Dataset;
import org.ga4gh.refcloud.api.drs.awss3accessobject.AwsS3AccessObject;
import org.ga4gh.refcloud.api.drs.drsobjectalias.DrsObjectAlias;
import org.ga4gh.refcloud.api.drs.drsobjectchecksum.DrsObjectChecksum;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "drs_object")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrsObject {

    @Id
    private String id;

    private String name;

    private Long size;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private String version;

    private String mimeType;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false) 
    private Dataset dataset;

    @OneToMany(mappedBy = "drsObject", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private Set<DrsObjectChecksum> checksums = new HashSet<>();

    @OneToMany(mappedBy = "drsObject", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private Set<AwsS3AccessObject> awsS3AccessObjects = new HashSet<>();

    @OneToMany(mappedBy = "drsObject", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private Set<DrsObjectAlias> aliases = new HashSet<>();
}
