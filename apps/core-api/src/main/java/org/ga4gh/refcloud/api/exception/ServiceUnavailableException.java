package org.ga4gh.refcloud.api.exception;

import java.io.Serial;
import java.time.Duration;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// retryAfter is sent as the Retry-After header
@Getter
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ServiceUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Duration retryAfter;

    public ServiceUnavailableException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }
}
