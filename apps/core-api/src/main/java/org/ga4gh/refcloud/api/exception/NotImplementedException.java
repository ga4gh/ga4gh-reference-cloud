package org.ga4gh.refcloud.api.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
public class NotImplementedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NotImplementedException(String message) {
        super(message);
    }
}
