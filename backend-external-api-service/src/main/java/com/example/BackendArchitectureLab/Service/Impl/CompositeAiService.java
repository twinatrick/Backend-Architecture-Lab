package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.AiJobPostingVo;
import com.example.BackendArchitectureLab.Service.IAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class CompositeAiService implements IAiService {

    private static final int MAX_TEXT_LENGTH = 30000;

    private final GeminiService geminiService;
    private final GroqService groqService;
    private final DeepSeekService deepSeekService;
    private final GitHubModelsService gitHubModelsService;

    @Override
    public List<AiJobPostingVo> analyzeJobPostings(String companyName, String htmlContent) {
        String cleanText = cleanHtmlToText(htmlContent);
        List<AiJobPostingVo> result;

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

    private List<AiJobPostingVo> tryService(IAiService service, String name, String companyName, String cleanText) {
        try {
            log.info("Trying {} API for company: {}", name, companyName);
            List<AiJobPostingVo> result = service.analyzeJobPostings(companyName, cleanText);
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
