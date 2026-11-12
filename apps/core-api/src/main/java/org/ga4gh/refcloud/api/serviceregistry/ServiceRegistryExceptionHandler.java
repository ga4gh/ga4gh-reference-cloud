package org.ga4gh.refcloud.api.serviceregistry;

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

// Returns Service Registry errors in the spec's Error format. Scoped to the Service Registry controller and
// ordered ahead of GlobalExceptionHandler, so other APIs keep their own error formats. Errors raised before a
// controller is chosen (e.g. 405, 406) keep Spring's default format
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "org.ga4gh.refcloud.api.serviceregistry")
public class ServiceRegistryExceptionHandler {

    static final String INTERNAL_SERVER_ERROR = "Internal server error";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ServiceRegistryErrorDTO> handleNotFound(ResourceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "Not found", exception.getMessage());
    }

    // errors Spring raises with their own status keep that status and their headers; anything else is a 500
    // with no internals in the response, logged in full here
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ServiceRegistryErrorDTO> handleException(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return ResponseEntity.status(status)
                    .headers(errorResponse.getHeaders())
                    .body(new ServiceRegistryErrorDTO(
                            status.value(),
                            title(status),
                            errorResponse.getBody().getDetail()));
        }

        log.error("unhandled exception in Service Registry request", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR, null);
    }

    private static ResponseEntity<ServiceRegistryErrorDTO> error(HttpStatus status, String title, String detail) {
        return ResponseEntity.status(status).body(new ServiceRegistryErrorDTO(status.value(), title, detail));
    }

    // e.g. "Method Not Allowed"
    private static String title(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known == null ? "Error " + status.value() : known.getReasonPhrase();
    }
}
