package com.example.BackendArchitectureLab.Exception;

import com.example.BackendArchitectureLab.Dto.Vo.ResponseType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
}
