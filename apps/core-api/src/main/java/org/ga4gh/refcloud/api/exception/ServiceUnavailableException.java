package org.ga4gh.refcloud.api.exception;

import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import lombok.Getter;

// retryAfter is sent as the Retry-After header
@Getter
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ServiceUnavailableException extends RuntimeException {

    private final Duration retryAfter;

    public ServiceUnavailableException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }
}
