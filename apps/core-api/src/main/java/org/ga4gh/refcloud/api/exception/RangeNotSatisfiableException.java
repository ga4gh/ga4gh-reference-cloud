package org.ga4gh.refcloud.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
public class RangeNotSatisfiableException extends RuntimeException {
    public RangeNotSatisfiableException(String message) {
        super(message);
    }
}
