package org.ga4gh.refcloud.api.core.tag;

import java.util.HashSet;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

import org.ga4gh.refcloud.api.core.dataset.Dataset;

@Entity
@Table(name = "tag")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tag {

    @Id
    private String id;

    private String tag;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "dataset_tag",
        joinColumns = @JoinColumn(name = "tag_id"),
        inverseJoinColumns = @JoinColumn(name = "dataset_id")
    )
    @Builder.Default
    private Set<Dataset> datasets = new HashSet<>();

    // Helper methods to keep both sides synchronized

    // datasets

    public void addDataset(Dataset dataset) {
        this.datasets.add(dataset);
        dataset.getTags().add(this);
    }

    public void removeDataset(Dataset dataset) {
        this.datasets.remove(dataset);
        dataset.getTags().remove(this);
    }
}
    