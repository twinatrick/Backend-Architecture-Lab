package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Service.IChatService;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import com.example.BackendArchitectureLab.Vo.ChatRequestVo;
import com.example.BackendArchitectureLab.Vo.ChatResponseVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService implements IChatService {

    private final AiPyServiceFeignClient aiPyServiceFeignClient;
    private final IUsageTrackService usageTrackService;
    private final WebClient aiPyWebClient;

    @Override
    public ChatResponseVo chat(List<Map<String, String>> messages, Double temperature) {
        ChatRequestVo request = ChatRequestVo.builder()
                .messages(messages)
                .temperature(temperature)
                .stream(false)
                .build();
        ChatResponseVo response = aiPyServiceFeignClient.chat(request);
        usageTrackService.track("chat", "chat", "message", (long) messages.size());
        return response;
    }

    @Override
    public Flux<String> streamChat(List<Map<String, String>> messages, Double temperature) {
        if (messages == null || messages.isEmpty()) {
            return Flux.empty();
        }

        ChatRequestVo request = ChatRequestVo.builder()
                .messages(messages)
                .temperature(temperature)
                .stream(true)
                .build();

        return aiPyWebClient.post()
                .uri("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(chunk -> chunk != null && !chunk.isBlank())
                .doOnComplete(() -> usageTrackService.track("chat", "chat-stream", "message", (long) messages.size()))
                .doOnError(err -> log.error("AI 串流調用異常", err));
    }
}
