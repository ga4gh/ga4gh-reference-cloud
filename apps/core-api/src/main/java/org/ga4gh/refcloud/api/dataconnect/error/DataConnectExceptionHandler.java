package org.ga4gh.refcloud.api.dataconnect.error;

import java.util.stream.Collectors;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.ga4gh.refcloud.api.exception.ServiceUnavailableException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import lombok.extern.slf4j.Slf4j;

// Returns Data Connect errors in the spec's ErrorResponse format. Scoped to the Data Connect
// controllers and ordered ahead of GlobalExceptionHandler, so DRS and Refget keep the app's
// existing error format. Errors raised before a controller is chosen are passed here by
// DataConnectHandlerlessExceptionResolver, and 401s by DataConnectAuthenticationEntryPoint
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "org.ga4gh.refcloud.api.dataconnect")
public class DataConnectExceptionHandler {

    static final String INTERNAL_SERVER_ERROR = "Internal server error";

    // 400: SQL, parameter, ga4gh_type() and limit errors
    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<DataConnectErrorResponseDTO> handleInvalidQuery(InvalidQueryException exception) {
        return error(HttpStatus.BAD_REQUEST, "Invalid query", exception.getMessage());
    }

    // 400: malformed requests (e.g. a bad page_token)
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<DataConnectErrorResponseDTO> handleBadRequest(BadRequestException exception) {
        return invalidRequest(exception.getMessage());
    }

    // 400: Bean Validation failures, e.g. "query: must not be blank"
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<DataConnectErrorResponseDTO> handleNotValid(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return invalidRequest(detail.isEmpty() ? "request is not valid" : detail);
    }

    // 400: the parser's message is not echoed, as it exposes internal class names
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<DataConnectErrorResponseDTO> handleNotReadable(HttpMessageNotReadableException exception) {
        return invalidRequest("request body is missing or is not valid JSON");
    }

    // 404: missing and inaccessible tables alike
    @ExceptionHandler(TableNotFoundException.class)
    public ResponseEntity<DataConnectErrorResponseDTO> handleTableNotFound(TableNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "Table not found", exception.getMessage());
    }

    // 404: missing, expired and other users' saved searches alike
    @ExceptionHandler(SearchNotFoundException.class)
    public ResponseEntity<DataConnectErrorResponseDTO> handleSearchNotFound(SearchNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "Search not found", exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<DataConnectErrorResponseDTO> handleNotFound(ResourceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "Not found", exception.getMessage());
    }

    // 503: saved search storage is full; Retry-After is when the next saved search expires
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<DataConnectErrorResponseDTO> handleServiceUnavailable(ServiceUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, exception.getRetryAfter().toSeconds())))
            .body(DataConnectErrorResponseDTO.of("Search storage full", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<DataConnectErrorResponseDTO> handleAccessDenied(AccessDeniedException exception) {
        return error(HttpStatus.FORBIDDEN, "Forbidden", "access to this resource is denied");
    }

    // errors Spring raises with their own status (e.g. 405 wrong method, 415 wrong content type)
    // keep that status and their headers (e.g. Allow); anything else is a 500 with no internals
    // in the response, logged in full here
    @ExceptionHandler(Exception.class)
    public ResponseEntity<DataConnectErrorResponseDTO> handleException(Exception exception) {
        // Spring's message for an unknown path mentions static resources
        if (exception instanceof NoResourceFoundException) {
            return error(HttpStatus.NOT_FOUND, "Not found", "no Data Connect endpoint at this path");
        }
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            String detail = errorResponse.getBody().getDetail();
            return ResponseEntity.status(status)
                .headers(errorResponse.getHeaders())
                .body(DataConnectErrorResponseDTO.of(title(status), detail == null ? title(status) : detail));
        }

        log.error("unhandled exception in Data Connect request", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR);
    }

    private static ResponseEntity<DataConnectErrorResponseDTO> invalidRequest(String detail) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }

    private static ResponseEntity<DataConnectErrorResponseDTO> error(HttpStatus status, String title, String detail) {
        return ResponseEntity.status(status).body(DataConnectErrorResponseDTO.of(title, detail));
    }

    // e.g. "Method Not Allowed"
    private static String title(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known == null ? "Error " + status.value() : known.getReasonPhrase();
    }
}
