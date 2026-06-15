package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.AiJobPostingDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompositeAiServiceTest {

    @Mock
    private GeminiService geminiService;

    @Mock
    private GroqService groqService;

    @Mock
    private DeepSeekService deepSeekService;

    @Mock
    private GitHubModelsService gitHubModelsService;

    @InjectMocks
    private CompositeAiService compositeAiService;

    private final String companyName = "Test Company";
    private List<AiJobPostingDto> mockResult;

    @BeforeEach
    void setUp() {
        AiJobPostingDto dto = new AiJobPostingDto();
        dto.setTitle("Software Engineer");
        mockResult = List.of(dto);
    }

    @Test
    @DisplayName("Should return Gemini result when Gemini succeeds")
    void testGeminiSucceedsFirst() {
        when(geminiService.analyzeJobPostings(companyName, "clean text")).thenReturn(mockResult);

        List<AiJobPostingDto> result = compositeAiService.analyzeJobPostings(companyName, "clean text");

        assertEquals(1, result.size());
        verify(geminiService).analyzeJobPostings(companyName, "clean text");
        verifyNoInteractions(groqService, deepSeekService, gitHubModelsService);
    }

    @Test
    @DisplayName("Should fallback to Groq when Gemini fails")
    void testFallbackToGroq() {
        when(geminiService.analyzeJobPostings(companyName, "clean text")).thenThrow(new RuntimeException("Gemini down"));
        when(groqService.analyzeJobPostings(companyName, "clean text")).thenReturn(mockResult);

        List<AiJobPostingDto> result = compositeAiService.analyzeJobPostings(companyName, "clean text");

        assertEquals(1, result.size());
        verify(geminiService).analyzeJobPostings(companyName, "clean text");
        verify(groqService).analyzeJobPostings(companyName, "clean text");
        verifyNoInteractions(deepSeekService, gitHubModelsService);
    }

    @Test
    @DisplayName("Should fallback to DeepSeek when Gemini and Groq fail")
    void testFallbackToDeepSeek() {
        when(geminiService.analyzeJobPostings(companyName, "clean text")).thenThrow(new RuntimeException("down"));
        when(groqService.analyzeJobPostings(companyName, "clean text")).thenThrow(new RuntimeException("down"));
        when(deepSeekService.analyzeJobPostings(companyName, "clean text")).thenReturn(mockResult);

        List<AiJobPostingDto> result = compositeAiService.analyzeJobPostings(companyName, "clean text");

        assertEquals(1, result.size());
        verify(deepSeekService).analyzeJobPostings(companyName, "clean text");
        verifyNoInteractions(gitHubModelsService);
    }

    @Test
    @DisplayName("Should fallback to GitHub Models when all others fail")
    void testFallbackToGitHubModels() {
        when(geminiService.analyzeJobPostings(companyName, "clean text")).thenThrow(new RuntimeException("down"));
        when(groqService.analyzeJobPostings(companyName, "clean text")).thenThrow(new RuntimeException("down"));
        when(deepSeekService.analyzeJobPostings(companyName, "clean text")).thenThrow(new RuntimeException("down"));
        when(gitHubModelsService.analyzeJobPostings(companyName, "clean text")).thenReturn(mockResult);

        List<AiJobPostingDto> result = compositeAiService.analyzeJobPostings(companyName, "clean text");

        assertEquals(1, result.size());
        verify(gitHubModelsService).analyzeJobPostings(companyName, "clean text");
    }

    @Test
    @DisplayName("Should return empty list when all AI services fail")
    void testAllServicesFail() {
        when(geminiService.analyzeJobPostings(companyName, "clean text")).thenThrow(new RuntimeException("down"));
        when(groqService.analyzeJobPostings(companyName, "clean text")).thenThrow(new RuntimeException("down"));
        when(deepSeekService.analyzeJobPostings(companyName, "clean text")).thenThrow(new RuntimeException("down"));
        when(gitHubModelsService.analyzeJobPostings(companyName, "clean text")).thenThrow(new RuntimeException("down"));

        List<AiJobPostingDto> result = compositeAiService.analyzeJobPostings(companyName, "clean text");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should skip to next service when service returns empty result")
    void testEmptyResultCausesFallback() {
        when(geminiService.analyzeJobPostings(companyName, "clean text")).thenReturn(List.of());
        when(groqService.analyzeJobPostings(companyName, "clean text")).thenReturn(mockResult);

        List<AiJobPostingDto> result = compositeAiService.analyzeJobPostings(companyName, "clean text");

        assertEquals(1, result.size());
        verify(geminiService).analyzeJobPostings(companyName, "clean text");
        verify(groqService).analyzeJobPostings(companyName, "clean text");
    }

    @Test
    @DisplayName("Should strip HTML tags from input")
    void testHtmlCleanOnAnalyze() {
        String html = "<html><body><p>Software Engineer at Test Company</p></body></html>";
        when(geminiService.analyzeJobPostings(companyName, "Software Engineer at Test Company"))
                .thenReturn(mockResult);

        List<AiJobPostingDto> result = compositeAiService.analyzeJobPostings(companyName, html);

        assertEquals(1, result.size());
        verify(geminiService).analyzeJobPostings(companyName, "Software Engineer at Test Company");
    }

    @Test
    @DisplayName("Should handle null HTML gracefully")
    void testNullHtml() {
        when(geminiService.analyzeJobPostings(companyName, "")).thenReturn(mockResult);

        List<AiJobPostingDto> result = compositeAiService.analyzeJobPostings(companyName, null);

        assertEquals(1, result.size());
        verify(geminiService).analyzeJobPostings(companyName, "");
    }
}
