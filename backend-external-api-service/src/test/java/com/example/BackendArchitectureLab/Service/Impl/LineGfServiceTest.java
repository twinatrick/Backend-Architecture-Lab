package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.ILineGfMessageDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ILineGfSessionDataAccess;
import com.example.BackendArchitectureLab.Entity.LineGfMessage;
import com.example.BackendArchitectureLab.Entity.LineGfSession;
import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Mapper.GfSessionMapper;
import com.example.BackendArchitectureLab.Service.ISttService;
import com.example.BackendArchitectureLab.Service.ITtsService;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import com.example.BackendArchitectureLab.Vo.ChatRequestVo;
import com.example.BackendArchitectureLab.Vo.ChatResponseVo;
import com.example.BackendArchitectureLab.Vo.LineGfSessionVo;
import com.linecorp.bot.client.LineBlobClient;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.response.BotApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LineGfServiceTest {

    @Mock
    private LineMessagingClient messagingClient;

    @Mock
    private LineBlobClient blobClient;

    @Mock
    private AiPyServiceFeignClient aiPyServiceFeignClient;

    @Mock
    private ITtsService ttsService;

    @Mock
    private ISttService sttService;

    @Mock
    private IUsageTrackService usageTrackService;

    @Mock
    private ILineGfSessionDataAccess sessionDataAccess;

    @Mock
    private ILineGfMessageDataAccess messageDataAccess;

    @Mock
    private GfSessionMapper gfSessionMapper;

    private LineGfService service;

    @BeforeEach
    void setUp() {
        service = new LineGfService(
                messagingClient,
                blobClient,
                aiPyServiceFeignClient,
                ttsService,
                sttService,
                usageTrackService,
                sessionDataAccess,
                messageDataAccess,
                gfSessionMapper
        );
    }

    @Test
    @DisplayName("handleText 遇 null 或空白 userId 應安全略過，不存取 Session 或下游")
    void handleText_ShouldSafelyIgnore_whenUserIdNullOrBlank() {
        assertDoesNotThrow(() -> service.handleText("token1", "Hello", null));
        assertDoesNotThrow(() -> service.handleText("token2", "Hello", ""));
        assertDoesNotThrow(() -> service.handleText("token3", "Hello", "   "));

        verifyNoInteractions(sessionDataAccess, messageDataAccess, messagingClient, aiPyServiceFeignClient);
    }

    @Test
    @DisplayName("handleAudio 遇 null 或空白 userId 應安全略過，不存取 Blob 或下游")
    void handleAudio_ShouldSafelyIgnore_whenUserIdNullOrBlank() {
        assertDoesNotThrow(() -> service.handleAudio("token1", "msg1", null));
        assertDoesNotThrow(() -> service.handleAudio("token2", "msg2", ""));
        assertDoesNotThrow(() -> service.handleAudio("token3", "msg3", "   "));

        verifyNoInteractions(blobClient, sessionDataAccess, messageDataAccess, messagingClient, sttService);
    }

    @Test
    @DisplayName("handleText 在啟用模式下應分別保存 user 與 assistant 訊息至 1:N 訊息表並回覆")
    void handleText_WhenActive_ShouldPersistMessagesInOneToManyTable() {
        UUID sessionId = UUID.randomUUID();
        LineGfSession session = new LineGfSession();
        session.setUserId("user-123");
        session.setActive(true);
        session.setGfName("小美");
        session.setId(sessionId);

        when(sessionDataAccess.findByUserId("user-123")).thenReturn(Optional.of(session));
        when(gfSessionMapper.toVo(session)).thenReturn(LineGfSessionVo.builder().gfName("小美").build());
        when(messageDataAccess.findRecentMessages(sessionId, 20)).thenReturn(Collections.emptyList());
        when(aiPyServiceFeignClient.chat(any(ChatRequestVo.class))).thenReturn(ChatResponseVo.builder().content("哈囉呀！").build());
        when(messagingClient.replyMessage(any(ReplyMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(BotApiResponse.class)));

        service.handleText("token-123", "今天過得好嗎？", "user-123");

        ArgumentCaptor<LineGfMessage> messageCaptor = ArgumentCaptor.forClass(LineGfMessage.class);
        verify(messageDataAccess, times(2)).save(messageCaptor.capture());

        List<LineGfMessage> saved = messageCaptor.getAllValues();
        assertEquals("user", saved.get(0).getRole());
        assertEquals("今天過得好嗎？", saved.get(0).getContent());
        assertEquals(sessionId, saved.get(0).getSessionId());

        assertEquals("assistant", saved.get(1).getRole());
        assertEquals("哈囉呀！", saved.get(1).getContent());
        assertEquals("小美", saved.get(1).getSenderName());
        assertEquals(sessionId, saved.get(1).getSessionId());
    }

    @Test
    @DisplayName("handleText 指令 #狀態 應透過 messageDataAccess 統計對話歷史數量")
    void handleCommand_Status_ShouldCountMessagesViaMessageDataAccess() {
        UUID sessionId = UUID.randomUUID();
        LineGfSession session = new LineGfSession();
        session.setUserId("user-456");
        session.setActive(true);
        session.setPrompt("溫柔的女友");
        session.setId(sessionId);

        when(sessionDataAccess.findByUserId("user-456")).thenReturn(Optional.of(session));
        when(messageDataAccess.countBySessionId(sessionId)).thenReturn(15L);
        when(messagingClient.replyMessage(any(ReplyMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(BotApiResponse.class)));

        service.handleText("token-status", "#狀態", "user-456");

        verify(messageDataAccess).countBySessionId(sessionId);
        verify(messagingClient).replyMessage(any(ReplyMessage.class));
    }

    @Test
    @DisplayName("handleText 當處於 pendingPrompt 狀態時應更新提示詞並清空歷史對話訊息")
    void handleText_WhenPendingPrompt_ShouldUpdatePromptAndClearHistory() {
        UUID sessionId = UUID.randomUUID();
        LineGfSession session = new LineGfSession();
        session.setUserId("user-789");
        session.setPendingPrompt(true);
        session.setId(sessionId);

        when(sessionDataAccess.findByUserId("user-789")).thenReturn(Optional.of(session));
        when(sessionDataAccess.save(any(LineGfSession.class))).thenReturn(session);
        when(messagingClient.replyMessage(any(ReplyMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(BotApiResponse.class)));

        service.handleText("token-prompt", "你是一位知性大姐姐", "user-789");

        ArgumentCaptor<LineGfSession> sessionCaptor = ArgumentCaptor.forClass(LineGfSession.class);
        verify(sessionDataAccess).save(sessionCaptor.capture());
        assertEquals("你是一位知性大姐姐", sessionCaptor.getValue().getPrompt());
        assertEquals(false, sessionCaptor.getValue().getPendingPrompt());
        verify(messageDataAccess).deleteBySessionId(sessionId);
        verify(messagingClient).replyMessage(any(ReplyMessage.class));
    }
}
