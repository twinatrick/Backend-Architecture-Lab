package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.AiJobPostingDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BaseOpenAiServiceTest {

    private TestBaseOpenAiService service;

    static class TestBaseOpenAiService extends BaseOpenAiService {
        private String apiKey = "test-key";
        private String apiUrl = "https://api.test.com/v1/chat/completions";
        private String modelName = "test-model";

        @Override
        protected String getApiKey() { return apiKey; }
        @Override
        protected String getApiUrl() { return apiUrl; }
        @Override
        protected String getModelName() { return modelName; }

        @Override
        public List<AiJobPostingDto> parseResponse(String response) {
            return super.parseResponse(response);
        }
    }

    @BeforeEach
    void setUp() {
        service = new TestBaseOpenAiService();
    }

    private static String buildApiResponse(String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"choices\":[{\"message\":{\"content\":\"" + escaped + "\"}}]}";
    }

    @Test
    @DisplayName("buildRequestBody should contain model, system message, user prompt, temperature and max_tokens")
    void buildRequestBodyContainsAllFields() {
        String body = service.buildRequestBody("test prompt");
        assertNotNull(body);
        assertTrue(body.contains("test-model"));
        assertTrue(body.contains("\"role\":\"system\""));
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("test prompt"));
        assertTrue(body.contains("\"temperature\":0.1"));
        assertTrue(body.contains("\"max_tokens\":8192"));
    }

    @Test
    @DisplayName("parseResponse should extract single job from valid response")
    void parseResponseValidSingleJob() {
        String content = "[{\"title\":\"Engineer\",\"url\":\"https://x.com\",\"description\":\"desc\",\"requirements\":\"req\",\"responsibilities\":\"resp\",\"salaryRange\":\"100k\"}]";
        List<AiJobPostingDto> result = service.parseResponse(buildApiResponse(content));
        assertEquals(1, result.size());
        assertEquals("Engineer", result.get(0).getTitle());
        assertEquals("https://x.com", result.get(0).getUrl());
        assertEquals("desc", result.get(0).getDescription());
        assertEquals("req", result.get(0).getRequirements());
        assertEquals("resp", result.get(0).getResponsibilities());
        assertEquals("100k", result.get(0).getSalaryRange());
    }

    @Test
    @DisplayName("parseResponse should extract multiple jobs")
    void parseResponseMultipleJobs() {
        String content = "[{\"title\":\"A\",\"description\":\"d1\"},{\"title\":\"B\",\"description\":\"d2\"}]";
        List<AiJobPostingDto> result = service.parseResponse(buildApiResponse(content));
        assertEquals(2, result.size());
        assertEquals("A", result.get(0).getTitle());
        assertEquals("B", result.get(1).getTitle());
    }

    @Test
    @DisplayName("parseResponse should return empty list for empty choices array")
    void parseResponseNoChoices() {
        String json = "{\"choices\":[]}";
        assertTrue(service.parseResponse(json).isEmpty());
    }

    @Test
    @DisplayName("parseResponse should return empty list when choices field is missing")
    void parseResponseMissingChoices() {
        assertTrue(service.parseResponse("{\"foo\":\"bar\"}").isEmpty());
    }

    @Test
    @DisplayName("parseResponse should return empty list when message is null")
    void parseResponseNullMessage() {
        assertTrue(service.parseResponse("{\"choices\":[{}]}").isEmpty());
    }

    @Test
    @DisplayName("parseResponse should return empty list when content has no JSON array")
    void parseResponseNoJsonArray() {
        String json = "{\"choices\":[{\"message\":{\"content\":\"plain text no array\"}}]}";
        assertTrue(service.parseResponse(json).isEmpty());
    }

    @Test
    @DisplayName("parseResponse should return empty list for malformed JSON")
    void parseResponseMalformedJson() {
        assertTrue(service.parseResponse("not even json").isEmpty());
    }

    @Test
    @DisplayName("parseResponse should fill missing optional fields with empty string")
    void parseResponseMissingOptionalFields() {
        String content = "[{\"title\":\"T\",\"description\":\"D\"}]";
        List<AiJobPostingDto> result = service.parseResponse(buildApiResponse(content));
        assertEquals(1, result.size());
        assertEquals("T", result.get(0).getTitle());
        assertEquals("D", result.get(0).getDescription());
        assertEquals("", result.get(0).getUrl());
        assertEquals("", result.get(0).getRequirements());
        assertEquals("", result.get(0).getResponsibilities());
        assertEquals("", result.get(0).getSalaryRange());
    }

    @Test
    @DisplayName("parseResponse should handle null field values gracefully")
    void parseResponseNullFieldValues() {
        String content = "[{\"title\":null,\"url\":null,\"description\":\"D\",\"requirements\":null,\"responsibilities\":null,\"salaryRange\":null}]";
        List<AiJobPostingDto> result = service.parseResponse(buildApiResponse(content));
        assertEquals(1, result.size());
        assertEquals("", result.get(0).getTitle());
        assertEquals("", result.get(0).getUrl());
        assertEquals("D", result.get(0).getDescription());
        assertEquals("", result.get(0).getRequirements());
        assertEquals("", result.get(0).getResponsibilities());
        assertEquals("", result.get(0).getSalaryRange());
    }
}
