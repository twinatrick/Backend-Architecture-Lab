package com.example.BackendArchitectureLab.Service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class GroqServiceTest {

    private GroqService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new GroqService();
        setField(service, "apiKey", "groq-test-key");
        setField(service, "apiUrl", "https://api.groq.com/openai/v1/chat/completions");
        setField(service, "model", "llama3-70b-8192");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("should extend BaseOpenAiService")
    void isBaseOpenAiServiceSubclass() {
        assertInstanceOf(BaseOpenAiService.class, service);
    }

    @Test
    @DisplayName("getApiKey should return configured key")
    void getApiKey() {
        assertEquals("groq-test-key", service.getApiKey());
    }

    @Test
    @DisplayName("getApiUrl should return configured URL")
    void getApiUrl() {
        assertEquals("https://api.groq.com/openai/v1/chat/completions", service.getApiUrl());
    }

    @Test
    @DisplayName("getModelName should return configured model")
    void getModelName() {
        assertEquals("llama3-70b-8192", service.getModelName());
    }
}
