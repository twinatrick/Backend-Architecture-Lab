package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import com.example.BackendArchitectureLab.Vo.ChatRequestVo;
import com.example.BackendArchitectureLab.Vo.ChatResponseVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService 單元測試")
class ChatServiceTest {

    @Mock
    private AiPyServiceFeignClient aiPyServiceFeignClient;

    @Mock
    private IUsageTrackService usageTrackService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();
        ExchangeFunction exchangeFunction = request -> {
            Flux<DataBuffer> bodyFlux = Flux.just("data: 你好\n\n", "data: ！我是\n\n", "data: AI 助理\n\n")
                    .map(str -> bufferFactory.wrap(str.getBytes(StandardCharsets.UTF_8)));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .body(bodyFlux)
                    .build());
        };

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        chatService = new ChatService(aiPyServiceFeignClient, usageTrackService, webClient);
    }

    @Test
    @DisplayName("同步對話應透過 Feign 調用並記錄用量")
    void chat_shouldInvokeFeignAndTrackUsage() {
        ChatResponseVo mockResponse = new ChatResponseVo();
        mockResponse.setContent("哈囉！");
        when(aiPyServiceFeignClient.chat(any(ChatRequestVo.class))).thenReturn(mockResponse);

        ChatResponseVo result = chatService.chat(List.of(Map.of("role", "user", "content", "hi")), 0.7);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("哈囉！");
        verify(usageTrackService, times(1)).track(eq("chat"), eq("chat"), eq("message"), eq(1L));
    }

    @Test
    @DisplayName("串流對話應透過 WebClient 接收 Flux 並正確發送 token")
    void streamChat_shouldReturnFluxOfTokens() {
        Flux<String> stream = chatService.streamChat(List.of(Map.of("role", "user", "content", "hi")), 0.7);

        StepVerifier.create(stream)
                .expectNext("你好")
                .expectNext("！我是")
                .expectNext("AI 助理")
                .verifyComplete();

        verify(usageTrackService, times(1)).track(eq("chat"), eq("chat-stream"), eq("message"), eq(1L));
    }

    @Test
    @DisplayName("訊息為空時 streamChat 應回傳空 Flux 且不發送請求")
    void streamChat_whenMessagesEmpty_shouldReturnEmptyFlux() {
        Flux<String> stream = chatService.streamChat(List.of(), 0.7);

        StepVerifier.create(stream)
                .verifyComplete();

        verifyNoInteractions(usageTrackService);
    }
}
