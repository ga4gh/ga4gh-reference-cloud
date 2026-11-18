package org.ga4gh.refcloud.api.exception;

import java.time.Instant;
import lombok.Data;

@Data
public class ErrorDetails {
    private String msg;
    private Integer statusCode;
    private Instant timestamp;

    public ErrorDetails(String msg, Integer statusCode, Instant timestamp) {
        this.msg = msg;
        this.statusCode = statusCode;
        this.timestamp = timestamp;
    }
}
