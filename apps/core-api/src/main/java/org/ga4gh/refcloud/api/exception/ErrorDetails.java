package org.ga4gh.refcloud.api.exception;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ErrorDetails {
    private String msg;
    private Integer statusCode;
    private LocalDateTime timestamp;

    public ErrorDetails(String msg, Integer statusCode, LocalDateTime timestamp) {
        this.msg = msg;
        this.statusCode = statusCode;
        this.timestamp = timestamp;
    }
}
