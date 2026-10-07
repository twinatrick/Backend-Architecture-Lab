package com.example.BackendArchitectureLab.Service.Impl;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class DeepSeekService extends BaseOpenAiService {

    private final RestTemplate restTemplate;
    private final Gson gson;

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Value("${deepseek.api.model}")
    private String model;

    @Override
    protected RestTemplate getRestTemplate() {
        return restTemplate;
    }

    @Override
    protected Gson getGson() {
        return gson;
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
}
