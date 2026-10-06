package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.IChatService;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import com.example.BackendArchitectureLab.Vo.ChatRequestVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiStreamController 單元測試")
class AiStreamControllerTest {

    @Mock
    private IChatService chatService;

    @Mock
    private SecurityUtil securityUtil;

    private AiStreamController controller;

    @BeforeEach
    void setUp() {
        controller = new AiStreamController(chatService, securityUtil);
    }

    @Test
    @DisplayName("streamChat 應回傳封裝為 ServerSentEvent 的 Flux")
    void streamChat_shouldReturnServerSentEvents() {
        when(securityUtil.requireCurrentUserId()).thenReturn(UUID.randomUUID());
        when(chatService.streamChat(any(), anyDouble()))
                .thenReturn(Flux.just("串流", "輸出", "測試"));

        ChatRequestVo request = ChatRequestVo.builder()
                .messages(List.of(Map.of("role", "user", "content", "測試")))
                .temperature(0.5)
                .build();

        Flux<ServerSentEvent<String>> sseFlux = controller.streamChat(request);

        StepVerifier.create(sseFlux)
                .expectNextMatches(sse -> "串流".equals(sse.data()))
                .expectNextMatches(sse -> "輸出".equals(sse.data()))
                .expectNextMatches(sse -> "測試".equals(sse.data()))
                .verifyComplete();

        verify(securityUtil, times(1)).requireCurrentUserId();
        verify(chatService, times(1)).streamChat(any(), eq(0.5));
    }

    @Test
    @DisplayName("未登入呼叫 streamChat 應拋出 IllegalStateException 且不呼叫服務")
    void streamChat_whenUnauthenticated_shouldThrowException() {
        when(securityUtil.requireCurrentUserId())
                .thenThrow(new IllegalStateException("Current user not found - no authentication"));

        ChatRequestVo request = ChatRequestVo.builder()
                .messages(List.of(Map.of("role", "user", "content", "測試")))
                .temperature(0.7)
                .build();

        assertThrows(IllegalStateException.class, () -> controller.streamChat(request));
        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("當請求為 null 或 messages 為空時應回傳空 Flux")
    void streamChat_whenRequestNull_shouldReturnEmpty() {
        when(securityUtil.requireCurrentUserId()).thenReturn(UUID.randomUUID());

        StepVerifier.create(controller.streamChat(null))
                .verifyComplete();

        ChatRequestVo emptyReq = ChatRequestVo.builder().messages(null).build();
        StepVerifier.create(controller.streamChat(emptyReq))
                .verifyComplete();

        verify(securityUtil, times(2)).requireCurrentUserId();
        verifyNoInteractions(chatService);
    }
}
