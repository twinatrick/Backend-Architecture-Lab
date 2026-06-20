package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.AiJobPostingDto;
import com.example.BackendArchitectureLab.Service.IAiService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Primary
@Service
public class CompositeAiService implements IAiService {

    private static final int MAX_TEXT_LENGTH = 30000;

    @Autowired
    private GeminiService geminiService;
    @Autowired
    private GroqService groqService;
    @Autowired
    private DeepSeekService deepSeekService;
    @Autowired
    private GitHubModelsService gitHubModelsService;

    @Override
    public List<AiJobPostingDto> analyzeJobPostings(String companyName, String htmlContent) {
        String cleanText = cleanHtmlToText(htmlContent);
        List<AiJobPostingDto> result;

        result = tryService(geminiService, "Gemini", companyName, cleanText);
        if (result != null) return result;

        result = tryService(groqService, "Groq", companyName, cleanText);
        if (result != null) return result;

        result = tryService(deepSeekService, "DeepSeek", companyName, cleanText);
        if (result != null) return result;

        result = tryService(gitHubModelsService, "GitHub Models", companyName, cleanText);
        if (result != null) return result;

        log.error("All AI services failed for company: {}", companyName);
        return List.of();
    }

    private List<AiJobPostingDto> tryService(IAiService service, String name, String companyName, String cleanText) {
        try {
            log.info("Trying {} API for company: {}", name, companyName);
            List<AiJobPostingDto> result = service.analyzeJobPostings(companyName, cleanText);
            if (result != null && !result.isEmpty()) {
                return result;
            }
            log.warn("{} returned empty result for company: {}", name, companyName);
        } catch (Exception e) {
            log.warn("{} API failed for company: {}", name, companyName);
        }
        return null;
    }

    private String cleanHtmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = Jsoup.parse(html).text();
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        return text;
    }
}
