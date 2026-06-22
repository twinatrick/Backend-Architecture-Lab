package com.example.BackendArchitectureLab.Service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekServiceTest {

    private DeepSeekService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DeepSeekService();
        setField(service, "apiKey", "deepseek-test-key");
        setField(service, "apiUrl", "https://api.deepseek.com/v1/chat/completions");
        setField(service, "model", "deepseek-chat");
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
        assertEquals("deepseek-test-key", service.getApiKey());
    }

    @Test
    @DisplayName("getApiUrl should return configured URL")
    void getApiUrl() {
        assertEquals("https://api.deepseek.com/v1/chat/completions", service.getApiUrl());
    }

    @Test
    @DisplayName("getModelName should return configured model")
    void getModelName() {
        assertEquals("deepseek-chat", service.getModelName());
    }
}
