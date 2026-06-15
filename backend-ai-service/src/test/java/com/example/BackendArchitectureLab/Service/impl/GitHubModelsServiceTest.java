package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.AiJobPostingDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitHubModelsServiceTest {

    private GitHubModelsService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new GitHubModelsService();
        setField(service, "apiKey", "gh-test-key");
        setField(service, "apiUrl", "https://models.inference.ai.azure.com/chat/completions");
        setField(service, "model", "gpt-4o");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        Field field = null;
        while (clazz != null && field == null) {
            try {
                field = clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        assertNotNull(field, "Field " + fieldName + " not found");
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
        assertEquals("gh-test-key", service.getApiKey());
    }

    @Test
    @DisplayName("getApiUrl should return configured URL")
    void getApiUrl() {
        assertEquals("https://models.inference.ai.azure.com/chat/completions", service.getApiUrl());
    }

    @Test
    @DisplayName("getModelName should return configured model")
    void getModelName() {
        assertEquals("gpt-4o", service.getModelName());
    }

    @Test
    @DisplayName("analyzeJobPostings should return empty list when apiKey is blank")
    void analyzeJobPostingsBlankApiKey() throws Exception {
        setField(service, "apiKey", "");
        List<AiJobPostingDto> result = service.analyzeJobPostings("TestCorp", "html");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("analyzeJobPostings should return empty list when apiKey is null")
    void analyzeJobPostingsNullApiKey() throws Exception {
        setField(service, "apiKey", null);
        List<AiJobPostingDto> result = service.analyzeJobPostings("TestCorp", "html");
        assertTrue(result.isEmpty());
    }

}
