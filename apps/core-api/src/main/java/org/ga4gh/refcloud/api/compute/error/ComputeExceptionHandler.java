package org.ga4gh.refcloud.api.compute.error;

import lombok.extern.slf4j.Slf4j;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// returns WES and TES errors as ComputeErrorResponse. scoped to the compute controllers and ordered ahead of
// GlobalExceptionHandler, so other APIs keep their own error formats
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "org.ga4gh.refcloud.api.compute")
public class ComputeExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ComputeErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    // errors Spring raises with their own status keep that status and their headers; anything else is a 500
    // with no internals in the response, logged in full here
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ComputeErrorResponse> handleException(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return ResponseEntity.status(status)
                    .headers(errorResponse.getHeaders())
                    .body(new ComputeErrorResponse(errorResponse.getBody().getDetail(), status.value()));
        }
        log.error("unhandled exception in a compute request", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    public static ResponseEntity<ComputeErrorResponse> error(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(new ComputeErrorResponse(msg, status.value()));
    }
}
