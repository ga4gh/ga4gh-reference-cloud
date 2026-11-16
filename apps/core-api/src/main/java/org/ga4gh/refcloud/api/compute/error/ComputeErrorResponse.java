package org.ga4gh.refcloud.api.compute.error;

import com.fasterxml.jackson.annotation.JsonProperty;

// the WES ErrorResponse shape ({"msg": ..., "status_code": ...}), used by both WES and TES: TES defines no error
// format, and one shape for the compute APIs is simpler for clients
public record ComputeErrorResponse(
        String msg, @JsonProperty("status_code") int statusCode) {}
