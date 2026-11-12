package org.ga4gh.refcloud.api.drs.drsobject;

import java.util.List;

// what compute needs to stage a DRS object as a workflow input (Task 8): its name, size, whether it's a manifest (whose
// bytes live in the database, so it can't be staged), and the https urls of its bytes in S3
public record DrsStagingInfo(String id, String name, Long size, boolean manifest, List<String> s3Urls) {}
