package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Config.AiServiceConfig;
import com.example.BackendArchitectureLab.Service.IAiService;
import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
        AiServiceConfig.class,
        GeminiService.class,
        GroqService.class,
        DeepSeekService.class,
        GitHubModelsService.class,
        CompositeAiService.class
}, properties = {
        "gemini.api.key=test-gemini-key",
        "gemini.api.url=https://generativelanguage.googleapis.com/v1beta/models/gemini-3.7-flash:generateContent",
        "deepseek.api.key=test-deepseek-key",
        "deepseek.api.url=https://api.deepseek.com/chat/completions",
        "deepseek.api.model=deepseek-v4-flash",
        "groq.api.key=test-groq-key",
        "groq.api.url=https://api.groq.com/openai/v1/chat/completions",
        "groq.api.model=llama-3.3-70b-versatile",
        "github.models.api.key=test-github-key",
        "github.models.api.url=https://models.inference.ai.azure.com/chat/completions",
        "github.models.api.model=gpt-4o-mini"
})
@DisplayName("AI Service Spring Context Wiring 裝配測試")
class AiServiceContextWiringTest {

    private final CompositeAiService compositeAiService;
    private final GeminiService geminiService;
    private final GroqService groqService;
    private final DeepSeekService deepSeekService;
    private final GitHubModelsService gitHubModelsService;
    private final RestTemplate restTemplate;
    private final Gson gson;
    private final IAiService primaryAiService;

    @Autowired
    public AiServiceContextWiringTest(
            CompositeAiService compositeAiService,
            GeminiService geminiService,
            GroqService groqService,
            DeepSeekService deepSeekService,
            GitHubModelsService gitHubModelsService,
            RestTemplate restTemplate,
            Gson gson,
            IAiService primaryAiService) {
        this.compositeAiService = compositeAiService;
        this.geminiService = geminiService;
        this.groqService = groqService;
        this.deepSeekService = deepSeekService;
        this.gitHubModelsService = gitHubModelsService;
        this.restTemplate = restTemplate;
        this.gson = gson;
        this.primaryAiService = primaryAiService;
    }

    @Test
    @DisplayName("所有 AI Provider 及 CompositeAiService 應由 Spring Context 正確完成建構子注入裝配")
    void allAiServicesShouldBeWiredSuccessfully() {
        assertThat(restTemplate).isNotNull();
        assertThat(gson).isNotNull();

        assertThat(geminiService).isNotNull().isInstanceOf(GeminiService.class);
        assertThat(groqService).isNotNull().isInstanceOf(GroqService.class);
        assertThat(deepSeekService).isNotNull().isInstanceOf(DeepSeekService.class);
        assertThat(gitHubModelsService).isNotNull().isInstanceOf(GitHubModelsService.class);

        assertThat(compositeAiService).isNotNull().isInstanceOf(CompositeAiService.class);
        assertThat(primaryAiService).isNotNull().isInstanceOf(CompositeAiService.class);
    }
}
