package org.ga4gh.refcloud.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
public class ContentTooLargeException extends RuntimeException {
    public ContentTooLargeException(String message) {
        super(message);
    }
}
