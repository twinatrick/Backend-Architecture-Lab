package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.AiJobPostingVo;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
public class GitHubModelsService extends BaseOpenAiService {

    @Value("${github.models.api.key}")
    private String apiKey;

    @Value("${github.models.api.url}")
    private String apiUrl;

    @Value("${github.models.api.model}")
    private String model;

    public GitHubModelsService(RestTemplate restTemplate, Gson gson) {
        super(restTemplate, gson);
    }

    @Override
    protected String getApiKey() {
        return apiKey;
    }

    @Override
    protected String getApiUrl() {
        return apiUrl;
    }

    @Override
    protected String getModelName() {
        return model;
    }

    @Override
    public List<AiJobPostingVo> analyzeJobPostings(String companyName, String htmlContent) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GitHub Models API key not configured, skipping");
            return List.of();
        }
        return super.analyzeJobPostings(companyName, htmlContent);
    }
}
