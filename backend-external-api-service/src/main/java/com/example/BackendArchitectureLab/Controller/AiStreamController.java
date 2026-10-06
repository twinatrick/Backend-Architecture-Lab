package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationAuth;
import com.example.BackendArchitectureLab.Service.IChatService;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import com.example.BackendArchitectureLab.Vo.ChatRequestVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/ai/stream")
@ApiControllerTag(name = "AI Stream", description = "AI 響應式串流推播 API")
@RequiredArgsConstructor
public class AiStreamController {

    private final IChatService chatService;
    private final SecurityUtil securityUtil;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperationAuth(summary = "AI 對話串流生成", description = "透過 Server-Sent Events (SSE) 逐字串流推播 AI 生成回應，支援非阻塞背壓與客戶端取消信號傳播。")
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatRequestVo request) {
        securityUtil.requireCurrentUserId();
        if (request == null || request.getMessages() == null) {
            return Flux.empty();
        }
        return chatService.streamChat(request.getMessages(), request.getTemperature())
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build())
                .doOnCancel(() -> log.info("客戶端中斷 SSE 連線，主動釋放串流橋接資源"));
    }
}
