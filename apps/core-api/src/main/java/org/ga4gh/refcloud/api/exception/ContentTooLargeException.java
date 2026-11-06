package org.ga4gh.refcloud.api.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
public class ContentTooLargeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ContentTooLargeException(String message) {
        super(message);
    }
}
