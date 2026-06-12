package com.example.BackendArchitectureLab.Exception;

import com.example.BackendArchitectureLab.Dto.Vo.ResponseType;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ResponseType<?>> handleAppException(AppException ex) {
        ResponseType<?> response = ResponseType.Fail(ex.getErrorType(), ex.getMessage(), ex.getHttpStatus());
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseType<?>> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? "Invalid request" : ex.getMessage();
        if ("Name already exists".equals(message)) {
            ResponseType<?> response = ResponseType.Fail("DUPLICATE_NAME", message, HttpStatus.CONFLICT.value());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        ResponseType<?> response = ResponseType.Fail("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ResponseType<?>> handleNotFound(EntityNotFoundException ex) {
        ResponseType<?> response = ResponseType.Fail("NOT_FOUND", "Not found", HttpStatus.NOT_FOUND.value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseType<?>> handleException(Exception ex) {
        ResponseType<?> response = ResponseType.Fail("INTERNAL_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR.value());
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
