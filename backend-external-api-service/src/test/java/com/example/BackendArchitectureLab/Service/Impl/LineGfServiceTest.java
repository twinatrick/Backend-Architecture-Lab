package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.ILineGfSessionDataAccess;
import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Mapper.GfSessionMapper;
import com.example.BackendArchitectureLab.Service.ISttService;
import com.example.BackendArchitectureLab.Service.ITtsService;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.client.LineBlobClient;
import com.linecorp.bot.client.LineMessagingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verifyNoInteractions;

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
    private GfSessionMapper gfSessionMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
                gfSessionMapper,
                objectMapper
        );
    }

    @Test
    @DisplayName("handleText 遇 null 或空白 userId 應安全略過，不存取 Session 或下游")
    void handleText_ShouldSafelyIgnore_whenUserIdNullOrBlank() {
        assertDoesNotThrow(() -> service.handleText("token1", "Hello", null));
        assertDoesNotThrow(() -> service.handleText("token2", "Hello", ""));
        assertDoesNotThrow(() -> service.handleText("token3", "Hello", "   "));

        verifyNoInteractions(sessionDataAccess, messagingClient, aiPyServiceFeignClient);
    }

    @Test
    @DisplayName("handleAudio 遇 null 或空白 userId 應安全略過，不存取 Blob 或下游")
    void handleAudio_ShouldSafelyIgnore_whenUserIdNullOrBlank() {
        assertDoesNotThrow(() -> service.handleAudio("token1", "msg1", null));
        assertDoesNotThrow(() -> service.handleAudio("token2", "msg2", ""));
        assertDoesNotThrow(() -> service.handleAudio("token3", "msg3", "   "));

        verifyNoInteractions(blobClient, sessionDataAccess, messagingClient, sttService);
    }
}
