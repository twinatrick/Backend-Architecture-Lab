package com.example.BackendArchitectureLab.Exception;

import com.example.BackendArchitectureLab.Vo.ResponseType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleIllegalArgument_shouldReturn409_whenDuplicateName() {
        IllegalArgumentException ex = new IllegalArgumentException("Name already exists");

        ResponseEntity<ResponseType<?>> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getCode());
        assertEquals("DUPLICATE_NAME", response.getBody().getErrorType());
        assertEquals("Name already exists", response.getBody().getMessage());
    }

    @Test
    void handleIllegalArgument_shouldReturn400WithOriginalMessage_whenValidationError() {
        IllegalArgumentException ex = new IllegalArgumentException("Skill level not found");

        ResponseEntity<ResponseType<?>> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("VALIDATION_ERROR", response.getBody().getErrorType());
        assertEquals("Skill level not found", response.getBody().getMessage());
    }

    @Test
    void handleMethodNotSupported_shouldReturn405() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("GET");

        ResponseEntity<ResponseType<?>> response = handler.handleMethodNotSupported(ex);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(405, response.getBody().getCode());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().getErrorType());
        assertEquals("HTTP method GET is not supported for this endpoint", response.getBody().getMessage());
    }

    @Test
    void handleMessageNotReadable_shouldReturn400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Invalid JSON");

        ResponseEntity<ResponseType<?>> response = handler.handleMessageNotReadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("MALFORMED_REQUEST_BODY", response.getBody().getErrorType());
    }

    @Test
    void handleMediaTypeNotSupported_shouldReturn415() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException("application/xml");

        ResponseEntity<ResponseType<?>> response = handler.handleMediaTypeNotSupported(ex);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(415, response.getBody().getCode());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", response.getBody().getErrorType());
    }
}
