package org.ga4gh.refcloud.api.exception;

import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    // 400 Bad Request
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentNotValidException.class
    })
    public ResponseEntity<ErrorDetails> handleHttpMessageNotReadableException(Exception exception, WebRequest request) {
        
        ErrorDetails errorDetails = new ErrorDetails(
            exception.getMessage(),
            HttpStatus.BAD_REQUEST.value(),
            LocalDateTime.now()
        );
        
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    // 403 Forbidden
    @ExceptionHandler({
        ForbiddenException.class,
    })
    public ResponseEntity<ErrorDetails> handleHttpMessageForbiddenException(Exception exception, WebRequest request) {

        ErrorDetails errorDetails = new ErrorDetails(
            exception.getMessage(),
            HttpStatus.FORBIDDEN.value(),
            LocalDateTime.now()
        );

        return new ResponseEntity<>(errorDetails, HttpStatus.FORBIDDEN);
    }


    // 404 Not Found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorDetails> handleResourceNotFoundException(ResourceNotFoundException exception, WebRequest request) {

        ErrorDetails errorDetails = new ErrorDetails(
            exception.getMessage(),
            HttpStatus.NOT_FOUND.value(),
            LocalDateTime.now()
        );

        return new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND);
    }

    // 413 Content Too Large
    @ExceptionHandler({
        ContentTooLargeException.class,
    })
    public ResponseEntity<ErrorDetails> handleHttpMessageContentTooLargeException(Exception exception, WebRequest request) {

        ErrorDetails errorDetails = new ErrorDetails(
            exception.getMessage(),
            HttpStatus.CONTENT_TOO_LARGE.value(),
            LocalDateTime.now()
        );

        return new ResponseEntity<>(errorDetails, HttpStatus.CONTENT_TOO_LARGE);
    }
}
