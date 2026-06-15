package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.AiJobPostingDto;
import com.example.BackendArchitectureLab.Service.IAiService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class BaseOpenAiService implements IAiService {

    protected static final int MAX_RETRIES = 2;

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

    protected final RestTemplate restTemplate = new RestTemplate();
    protected final Gson gson = new Gson();

    protected abstract String getApiKey();
    protected abstract String getApiUrl();
    protected abstract String getModelName();

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
                headers.setBearerAuth(getApiKey());

                HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

                log.info("Calling {} API for company: {} (attempt {}/{})", getModelName(), companyName, attempt + 1, MAX_RETRIES + 1);

                String response = restTemplate.postForObject(getApiUrl(), request, String.class);

                if (response == null) {
                    log.warn("{} API returned null response for company: {} (attempt {})", getModelName(), companyName, attempt + 1);
                    continue;
                }

                previousResponse = response;
                List<AiJobPostingDto> result = parseResponse(response);

                if (isValidJobList(result)) return result;
                log.warn("{} response format invalid for company: {} (attempt {})", getModelName(), companyName, attempt + 1);

            } catch (Exception e) {
                if (e instanceof HttpStatusCodeException) {
                    int status = ((HttpStatusCodeException) e).getStatusCode().value();
                    if (status == 429) {
                        log.warn("{} API token不足 (429 Too Many Requests) for company: {}", getModelName(), companyName);
                        return List.of();
                    }
                }
                log.error("Failed to call {} API for company: {} (attempt {})", getModelName(), companyName, attempt + 1);
            }
        }
        return List.of();
    }

    protected String buildRequestBody(String prompt) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", getModelName());

        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", SYSTEM_INSTRUCTION);
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.1);
        requestBody.addProperty("max_tokens", 8192);

        return gson.toJson(requestBody);
    }

    private String buildRetryRequestBody(String prompt, String modelResponse, String correction) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", getModelName());

        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", SYSTEM_INSTRUCTION);
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        JsonObject assistantMessage = new JsonObject();
        assistantMessage.addProperty("role", "assistant");
        assistantMessage.addProperty("content", modelResponse);
        messages.add(assistantMessage);

        JsonObject correctionMessage = new JsonObject();
        correctionMessage.addProperty("role", "user");
        correctionMessage.addProperty("content", correction);
        messages.add(correctionMessage);

        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.1);
        requestBody.addProperty("max_tokens", 8192);

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
            JsonArray choices = obj.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                if (message != null) {
                    JsonElement content = message.get("content");
                    if (content != null) return content.getAsString();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract text from {} raw response", getModelName());
        }
        return rawResponse;
    }

    protected List<AiJobPostingDto> parseResponse(String response) {
        try {
            JsonObject responseObj = gson.fromJson(response, JsonObject.class);
            JsonArray choices = responseObj.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                log.warn("No choices found in {} response", getModelName());
                return List.of();
            }

            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            JsonObject message = firstChoice.getAsJsonObject("message");
            if (message == null) {
                log.warn("No message found in {} response choice", getModelName());
                return List.of();
            }

            String text = message.get("content").getAsString();

            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start == -1 || end == -1 || end <= start) {
                log.warn("No JSON array found in {} response text: {}", getModelName(), text);
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
            log.error("Failed to parse {} response", getModelName());
            return List.of();
        }
    }
}
