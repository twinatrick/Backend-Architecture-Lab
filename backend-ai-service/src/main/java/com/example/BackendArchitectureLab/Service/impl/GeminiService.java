package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.AiJobPostingDto;
import com.example.BackendArchitectureLab.Service.IAiService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Primary AI service implementation using Google Gemini REST API.
 */
@Slf4j
@Service
public class GeminiService implements IAiService {

    private static final int MAX_RETRIES = 2;

    private static final String SYSTEM_INSTRUCTION = "你是一個專業的職缺分析助手，請嚴格遵守以下規則：\n" +
            "1. 只回傳純 JSON 陣列，不可包含 Markdown 程式碼區塊、說明文字或任何前綴後綴。\n" +
            "2. 每個職缺物件必須包含以下英文欄位：\n" +
            "   - title（職缺名稱）\n" +
            "   - url（職缺網址，無則空字串）\n" +
            "   - description（職缺描述）\n" +
            "   - requirements（需求條件）\n" +
            "   - responsibilities（工作職責）\n" +
            "   - salaryRange（薪資範圍，無則「面議」）\n" +
            "3. 若完全找不到任何職缺，請回傳 []\n" +
            "4. 所有欄位內容請以繁體中文填寫。\n" +
            "5. 範例：[{\"title\":\"軟體工程師\",\"url\":\"https://...\",\"description\":\"開發後端服務\",\"requirements\":\"熟悉Java\",\"responsibilities\":\"API開發\",\"salaryRange\":\"面議\"}]";

    private final RestTemplate restTemplate;
    private final Gson gson;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    public GeminiService() {
        this.restTemplate = new RestTemplate();
        this.gson = new Gson();
    }

    @Override
    public List<AiJobPostingDto> analyzeJobPostings(String companyName, String htmlContent) {
        String truncatedHtml = htmlContent.length() > 60000
                ? htmlContent.substring(0, 60000)
                : htmlContent;

        String userPrompt = String.format("""
                公司名稱: %s
                
                HTML 內容:
                %s
                """, companyName, truncatedHtml);

        String previousResponse = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String requestBody;
                if (attempt == 0) {
                    requestBody = buildRequestBody(userPrompt);
                } else {
                    String modelResponse = extractTextFromRawResponse(previousResponse);
                    String correction = "回傳格式不符。只回傳純 JSON 陣列，每個物件須含 title, url, description, requirements, responsibilities, salaryRange，不可加 Markdown 或說明文字。";
                    requestBody = buildRetryRequestBody(userPrompt, modelResponse, correction);
                }

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

                String url = apiUrl + "?key=" + apiKey;
                log.info("Calling Gemini API for company: {} (attempt {}/{})", companyName, attempt + 1, MAX_RETRIES + 1);

                String response = restTemplate.postForObject(url, request, String.class);

                if (response == null) {
                    log.warn("Gemini API returned null response for company: {} (attempt {})", companyName, attempt + 1);
                    continue;
                }

                previousResponse = response;
                List<AiJobPostingDto> result = parseResponse(response);

                if (isValidJobList(result)) return result;
                log.warn("Gemini response format invalid for company: {} (attempt {})", companyName, attempt + 1);

            } catch (Exception e) {
                if (e instanceof HttpStatusCodeException) {
                    int status = ((HttpStatusCodeException) e).getStatusCode().value();
                    if (status == 429) {
                        log.warn("Gemini API token不足 (429 Too Many Requests) for company: {}", companyName);
                        return List.of();
                    }
                }
                log.error("Failed to call Gemini API for company: {} (attempt {})", companyName, attempt + 1);
            }
        }
        return List.of();
    }

    private String buildRequestBody(String prompt) {
        JsonObject requestBody = new JsonObject();

        // System instruction
        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemPart = new JsonObject();
        systemPart.addProperty("text", SYSTEM_INSTRUCTION);
        systemParts.add(systemPart);
        systemInstruction.add("parts", systemParts);
        requestBody.add("system_instruction", systemInstruction);

        // User content
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        requestBody.add("contents", contents);

        // Generation config
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.1);
        generationConfig.addProperty("maxOutputTokens", 8192);
        requestBody.add("generationConfig", generationConfig);

        return gson.toJson(requestBody);
    }

    private String buildRetryRequestBody(String prompt, String modelResponse, String correction) {
        JsonObject requestBody = new JsonObject();

        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemPart = new JsonObject();
        systemPart.addProperty("text", SYSTEM_INSTRUCTION);
        systemParts.add(systemPart);
        systemInstruction.add("parts", systemParts);
        requestBody.add("system_instruction", systemInstruction);

        JsonArray contents = new JsonArray();

        JsonObject userContent1 = new JsonObject();
        userContent1.addProperty("role", "user");
        JsonArray userParts1 = new JsonArray();
        JsonObject userPart1 = new JsonObject();
        userPart1.addProperty("text", prompt);
        userParts1.add(userPart1);
        userContent1.add("parts", userParts1);
        contents.add(userContent1);

        JsonObject modelContent = new JsonObject();
        modelContent.addProperty("role", "model");
        JsonArray modelParts = new JsonArray();
        JsonObject modelPart = new JsonObject();
        modelPart.addProperty("text", modelResponse);
        modelParts.add(modelPart);
        modelContent.add("parts", modelParts);
        contents.add(modelContent);

        JsonObject userContent2 = new JsonObject();
        userContent2.addProperty("role", "user");
        JsonArray userParts2 = new JsonArray();
        JsonObject userPart2 = new JsonObject();
        userPart2.addProperty("text", correction);
        userParts2.add(userPart2);
        userContent2.add("parts", userParts2);
        contents.add(userContent2);

        requestBody.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.1);
        generationConfig.addProperty("maxOutputTokens", 8192);
        requestBody.add("generationConfig", generationConfig);

        return gson.toJson(requestBody);
    }

    private boolean isValidJobList(List<AiJobPostingDto> jobs) {
        if (jobs == null || jobs.isEmpty()) return false;
        for (AiJobPostingDto job : jobs) {
            if (job.getTitle() == null || job.getTitle().isBlank()) return false;
            if (job.getDescription() == null || job.getDescription().isBlank()) return false;
        }
        return true;
    }

    private String extractTextFromRawResponse(String rawResponse) {
        try {
            JsonObject obj = gson.fromJson(rawResponse, JsonObject.class);
            JsonArray candidates = obj.getAsJsonArray("candidates");
            if (candidates != null && candidates.size() > 0) {
                JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
                if (content != null) {
                    JsonArray parts = content.getAsJsonArray("parts");
                    if (parts != null && parts.size() > 0) {
                        JsonElement text = parts.get(0).getAsJsonObject().get("text");
                        if (text != null) return text.getAsString();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract text from Gemini raw response");
        }
        return rawResponse;
    }

    private List<AiJobPostingDto> parseResponse(String response) {
        try {
            JsonObject responseObj = gson.fromJson(response, JsonObject.class);
            JsonArray candidates = responseObj.getAsJsonArray("candidates");
            if (candidates == null || candidates.size() == 0) {
                log.warn("No candidates found in Gemini response");
                return List.of();
            }

            JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
            JsonObject content = firstCandidate.getAsJsonObject("content");
            JsonArray parts = content.getAsJsonArray("parts");
            if (parts == null || parts.size() == 0) {
                log.warn("No parts found in Gemini response candidate");
                return List.of();
            }

            String text = parts.get(0).getAsJsonObject().get("text").getAsString();

            // Extract JSON array from text
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start == -1 || end == -1 || end <= start) {
                log.warn("No JSON array found in Gemini response text: {}", text);
                return List.of();
            }

            String jsonArray = text.substring(start, end + 1);
            JsonArray jobs = gson.fromJson(jsonArray, JsonArray.class);

            List<AiJobPostingDto> result = new ArrayList<>();
            for (JsonElement jobElement : jobs) {
                JsonObject jobObj = jobElement.getAsJsonObject();
                AiJobPostingDto dto = new AiJobPostingDto();
                
                JsonElement title = jobObj.get("title");
                dto.setTitle(title != null && !title.isJsonNull() ? title.getAsString() : "");
                
                JsonElement url = jobObj.get("url");
                dto.setUrl(url != null && !url.isJsonNull() ? url.getAsString() : "");
                
                JsonElement description = jobObj.get("description");
                dto.setDescription(description != null && !description.isJsonNull() ? description.getAsString() : "");
                
                JsonElement requirements = jobObj.get("requirements");
                dto.setRequirements(requirements != null && !requirements.isJsonNull() ? requirements.getAsString() : "");
                
                JsonElement responsibilities = jobObj.get("responsibilities");
                dto.setResponsibilities(responsibilities != null && !responsibilities.isJsonNull() ? responsibilities.getAsString() : "");
                
                JsonElement salaryRange = jobObj.get("salaryRange");
                dto.setSalaryRange(salaryRange != null && !salaryRange.isJsonNull() ? salaryRange.getAsString() : "");
                
                result.add(dto);
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to parse Gemini response");
            return List.of();
        }
    }
}
