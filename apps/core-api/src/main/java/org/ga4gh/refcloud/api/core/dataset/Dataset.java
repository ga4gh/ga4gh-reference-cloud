package org.ga4gh.refcloud.api.core.dataset;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.Set;
import org.ga4gh.refcloud.api.core.tag.Tag;
import org.ga4gh.refcloud.api.drs.drsobject.DrsObject;


@Entity
@Table(name = "dataset")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dataset {

    @Id
    private String id;

    private String name;

    private String description;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "dataset_tag",
        joinColumns = @JoinColumn(name = "dataset_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DrsObject> drsObjects = new ArrayList<>();

    // Helper methods to keep both sides synchronized

    // tags

    public void addTag(Tag tag) {
        this.tags.add(tag);
        tag.getDatasets().add(this);
    }

    public void removeTag(Tag tag) {
        this.tags.remove(tag);
        tag.getDatasets().remove(this);
    }

    // drsObjects

    public void addDrsObject(DrsObject drsObject) {
        drsObjects.add(drsObject);
        drsObject.setDataset(this);
    }

    public void removeDrsObject(DrsObject drsObject) {
        drsObjects.remove(drsObject);
        drsObject.setDataset(null);
    }
}
