package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.AiJobPostingDto;
import com.example.BackendArchitectureLab.Service.IAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeminiServiceTest {

    private GeminiService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new GeminiService();
        // set final restTemplate not possible on JDK 21; skip mock-based tests
        setField(service, "apiKey", "gemini-test-key");
        setField(service, "apiUrl", "https://generativelanguage.googleapis.com/v1/models/gemini-pro:generateContent");
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

    private static Object invokePrivate(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private String buildGeminiApiResponse(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + escaped + "\"}]}}]}";
    }

    @Test
    @DisplayName("should implement IAiService")
    void implementsIAiService() {
        assertInstanceOf(IAiService.class, service);
    }

    @Test
    @DisplayName("buildRequestBody should generate Gemini-compatible request body")
    void buildRequestBody() throws Exception {
        String body = (String) invokePrivate(service, "buildRequestBody", new Class<?>[]{String.class}, "test prompt");
        assertNotNull(body);
        assertTrue(body.contains("system_instruction"));
        assertTrue(body.contains("contents"));
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("test prompt"));
        assertTrue(body.contains("\"temperature\":0.1"));
        assertTrue(body.contains("\"maxOutputTokens\":8192"));
    }

    @Test
    @DisplayName("parseResponse should extract valid job from candidates")
    void parseResponseValid() throws Exception {
        String content = "[{\"title\":\"Engineer\",\"description\":\"desc\"}]";
        String json = buildGeminiApiResponse(content);
        @SuppressWarnings("unchecked")
        List<AiJobPostingDto> result = (List<AiJobPostingDto>) invokePrivate(service, "parseResponse", new Class<?>[]{String.class}, json);
        assertEquals(1, result.size());
        assertEquals("Engineer", result.get(0).getTitle());
    }

    @Test
    @DisplayName("parseResponse should return empty list for empty candidates")
    void parseResponseEmptyCandidates() throws Exception {
        String json = "{\"candidates\":[]}";
        @SuppressWarnings("unchecked")
        List<AiJobPostingDto> result = (List<AiJobPostingDto>) invokePrivate(service, "parseResponse", new Class<?>[]{String.class}, json);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseResponse should return empty list for missing candidates")
    void parseResponseMissingCandidates() throws Exception {
        @SuppressWarnings("unchecked")
        List<AiJobPostingDto> result = (List<AiJobPostingDto>) invokePrivate(service, "parseResponse", new Class<?>[]{String.class}, "{}");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseResponse should return empty list for no parts in content")
    void parseResponseNoParts() throws Exception {
        String json = "{\"candidates\":[{\"content\":{\"parts\":[]}}]}";
        @SuppressWarnings("unchecked")
        List<AiJobPostingDto> result = (List<AiJobPostingDto>) invokePrivate(service, "parseResponse", new Class<?>[]{String.class}, json);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseResponse should return empty list when text has no JSON array")
    void parseResponseNoJsonArray() throws Exception {
        String json = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"plain text\"}]}}]}";
        @SuppressWarnings("unchecked")
        List<AiJobPostingDto> result = (List<AiJobPostingDto>) invokePrivate(service, "parseResponse", new Class<?>[]{String.class}, json);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseResponse should return empty list for malformed JSON")
    void parseResponseMalformed() throws Exception {
        @SuppressWarnings("unchecked")
        List<AiJobPostingDto> result = (List<AiJobPostingDto>) invokePrivate(service, "parseResponse", new Class<?>[]{String.class}, "bad json");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("extractTextFromRawResponse should extract text from Gemini response")
    void extractTextFromRawResponse() throws Exception {
        String json = buildGeminiApiResponse("hello world");
        String text = (String) invokePrivate(service, "extractTextFromRawResponse", new Class<?>[]{String.class}, json);
        assertEquals("hello world", text);
    }

    @Test
    @DisplayName("extractTextFromRawResponse should return raw string when parsing fails")
    void extractTextFromRawResponseFallback() throws Exception {
        String text = (String) invokePrivate(service, "extractTextFromRawResponse", new Class<?>[]{String.class}, "not json");
        assertEquals("not json", text);
    }
}
