package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.ISttService;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import com.linecorp.bot.client.LineBlobClient;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.response.BotApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LineDiaryServiceTest {

    @Mock
    private LineMessagingClient messagingClient;

    @Mock
    private LineBlobClient blobClient;

    @Mock
    private ISttService sttService;

    @Mock
    private IUsageTrackService usageTrackService;

    @Mock
    private MessageContentResponse messageContentResponse;

    private LineDiaryService lineDiaryService;

    @BeforeEach
    void setUp() {
        lineDiaryService = new LineDiaryService(
                Optional.of(messagingClient),
                Optional.of(blobClient),
                sttService,
                usageTrackService
        );
    }

    @Test
    void handleText_whenConfigured_shouldTrackAndReply() {
        CompletableFuture<BotApiResponse> future = CompletableFuture.completedFuture(mock(BotApiResponse.class));
        when(messagingClient.replyMessage(any(ReplyMessage.class))).thenReturn(future);

        lineDiaryService.handleText("token-1", "測試訊息");

        verify(usageTrackService).track("line-diary", "chat", "char", 4L);
        verify(messagingClient).replyMessage(any(ReplyMessage.class));
    }

    @Test
    void handleText_whenNotConfigured_shouldThrowException() {
        LineDiaryService unconfiguredService = new LineDiaryService(
                Optional.empty(),
                Optional.of(blobClient),
                sttService,
                usageTrackService
        );

        assertThrows(IllegalStateException.class, () -> unconfiguredService.handleText("token-1", "hello"));
    }

    @Test
    void handleAudio_whenSuccessful_shouldReplyRecognizedText() throws Exception {
        InputStream stream = new ByteArrayInputStream("audio data".getBytes());
        when(messageContentResponse.getStream()).thenReturn(stream);
        when(blobClient.getMessageContent("msg-1")).thenReturn(CompletableFuture.completedFuture(messageContentResponse));
        when(sttService.recognizeAndTrack(any(byte[].class), eq("zh"), eq("line-diary"))).thenReturn("語音內容文字");
        when(messagingClient.replyMessage(any(ReplyMessage.class))).thenReturn(CompletableFuture.completedFuture(mock(BotApiResponse.class)));

        lineDiaryService.handleAudio("token-1", "msg-1");

        verify(sttService).recognizeAndTrack(any(byte[].class), eq("zh"), eq("line-diary"));
        verify(messagingClient).replyMessage(any(ReplyMessage.class));
        verify(messageContentResponse).close();
    }

    @Test
    void handleAudio_whenSttEmpty_shouldReplyUnclear() throws Exception {
        InputStream stream = new ByteArrayInputStream("audio data".getBytes());
        when(messageContentResponse.getStream()).thenReturn(stream);
        when(blobClient.getMessageContent("msg-1")).thenReturn(CompletableFuture.completedFuture(messageContentResponse));
        when(sttService.recognizeAndTrack(any(byte[].class), eq("zh"), eq("line-diary"))).thenReturn("");
        when(messagingClient.replyMessage(any(ReplyMessage.class))).thenReturn(CompletableFuture.completedFuture(mock(BotApiResponse.class)));

        lineDiaryService.handleAudio("token-1", "msg-1");

        verify(messagingClient).replyMessage(any(ReplyMessage.class));
    }

    @Test
    void handleAudio_whenInterrupted_shouldRestoreInterruptAndReply() {
        CompletableFuture<MessageContentResponse> interruptedFuture = new CompletableFuture<>() {
            @Override
            public MessageContentResponse get() throws InterruptedException {
                throw new InterruptedException("Thread interrupted");
            }
        };
        when(blobClient.getMessageContent("msg-1")).thenReturn(interruptedFuture);
        when(messagingClient.replyMessage(any(ReplyMessage.class))).thenReturn(CompletableFuture.completedFuture(mock(BotApiResponse.class)));

        lineDiaryService.handleAudio("token-1", "msg-1");

        verify(messagingClient).replyMessage(any(ReplyMessage.class));
    }

    @Test
    void handleAudio_whenException_shouldReplyFailure() {
        when(blobClient.getMessageContent("msg-1")).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Network error")));
        when(messagingClient.replyMessage(any(ReplyMessage.class))).thenReturn(CompletableFuture.completedFuture(mock(BotApiResponse.class)));

        lineDiaryService.handleAudio("token-1", "msg-1");

        verify(messagingClient).replyMessage(any(ReplyMessage.class));
    }
}
