package org.ga4gh.refcloud.api.compute.health;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Instant;

// a compute platform's health, as the "health" extension property of its WES and TES service-info (D10):
// {"status": "healthy" | "unhealthy", "checkedAt": "<ISO 8601>"}. checkedAt is absent until the first check completes
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComputeHealth(Status status, Instant checkedAt) {

    public static final ComputeHealth NOT_YET_CHECKED = new ComputeHealth(Status.UNHEALTHY, null);

    // not part of the JSON shape ({status, checkedAt})
    @JsonIgnore
    public boolean isHealthy() {
        return status == Status.HEALTHY;
    }

    public enum Status {
        HEALTHY("healthy"),
        UNHEALTHY("unhealthy");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }
}
