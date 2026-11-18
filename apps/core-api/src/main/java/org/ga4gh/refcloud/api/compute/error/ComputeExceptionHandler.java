package org.ga4gh.refcloud.api.compute.error;

import lombok.extern.slf4j.Slf4j;
import org.ga4gh.refcloud.api.compute.tes.ExecutorException;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.ga4gh.refcloud.api.exception.ForbiddenException;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// returns WES and TES errors as ComputeErrorResponse. scoped to the WES and TES controllers and ordered ahead of
// GlobalExceptionHandler, so other APIs keep their own error formats. the researcher UI's /compute-platforms
// (compute.platform) isn't a GA4GH API: it uses GlobalExceptionHandler, like /datasets
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {"org.ga4gh.refcloud.api.compute.wes", "org.ga4gh.refcloud.api.compute.tes"})
public class ComputeExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ComputeErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ComputeErrorResponse> handleBadRequest(BadRequestException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ComputeErrorResponse> handleForbidden(ForbiddenException exception) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    // a failed @PreAuthorize check (no approved visa for the platform)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ComputeErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return error(HttpStatus.FORBIDDEN, "access to this compute platform has not been approved");
    }

    // the executor couldn't be reached while handling the request (e.g. cancel)
    @ExceptionHandler(ExecutorException.class)
    public ResponseEntity<ComputeErrorResponse> handleExecutor(ExecutorException exception) {
        log.warn("executor error: {}", exception.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "the compute platform's executor is unavailable; try again");
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
