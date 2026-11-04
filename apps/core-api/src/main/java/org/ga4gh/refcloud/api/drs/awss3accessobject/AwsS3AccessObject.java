package org.ga4gh.refcloud.api.drs.awss3accessobject;

import org.ga4gh.refcloud.api.drs.drsobject.DrsObject;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "aws_s3_access_object")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AwsS3AccessObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String region;

    private String bucket;

    private String key;

    @ManyToOne
    @JoinColumn(name = "drs_object_id")
    private DrsObject drsObject;
}
