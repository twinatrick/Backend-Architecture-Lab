package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.ILineDiaryService;
import com.example.BackendArchitectureLab.Service.ILineGfService;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.FollowEvent;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.AudioMessageContent;
import com.linecorp.bot.model.event.message.ImageMessageContent;
import com.linecorp.bot.model.event.message.MessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.source.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LineWebhookServiceTest {

    private LineWebhookService service;

    @Mock
    private ILineGfService lineGfService;

    @Mock
    private ILineDiaryService lineDiaryService;

    @BeforeEach
    void setUp() {
        service = new LineWebhookService();
    }

    @Test
    @DisplayName("dispatchEvents 遇 TextMessageContent 應分發給 ILineGfService")
    void dispatchEvents_ShouldDispatchTextMessage_ToGfService() {
        MessageEvent<TextMessageContent> event = mock(MessageEvent.class);
        TextMessageContent textContent = mock(TextMessageContent.class);
        Source source = mock(Source.class);

        when(event.getMessage()).thenReturn(textContent);
        when(event.getReplyToken()).thenReturn("token-123");
        when(event.getSource()).thenReturn(source);
        when(source.getUserId()).thenReturn("user-456");
        when(textContent.getText()).thenReturn("Hello GF");

        service.dispatchEvents(List.of(event), lineGfService);

        verify(lineGfService).handleText("token-123", "Hello GF", "user-456");
    }

    @Test
    @DisplayName("dispatchEvents 遇 TextMessageContent 應分發給 ILineDiaryService")
    void dispatchEvents_ShouldDispatchTextMessage_ToDiaryService() {
        MessageEvent<TextMessageContent> event = mock(MessageEvent.class);
        TextMessageContent textContent = mock(TextMessageContent.class);

        when(event.getMessage()).thenReturn(textContent);
        when(event.getReplyToken()).thenReturn("token-diary");
        when(textContent.getText()).thenReturn("Diary Entry");

        service.dispatchEvents(List.of(event), lineDiaryService);

        verify(lineDiaryService).handleText("token-diary", "Diary Entry");
    }

    @Test
    @DisplayName("dispatchEvents 遇 AudioMessageContent 應分發給 ILineGfService")
    void dispatchEvents_ShouldDispatchAudioMessage_ToGfService() {
        MessageEvent<AudioMessageContent> event = mock(MessageEvent.class);
        AudioMessageContent audioContent = mock(AudioMessageContent.class);
        Source source = mock(Source.class);

        when(event.getMessage()).thenReturn(audioContent);
        when(event.getReplyToken()).thenReturn("token-audio");
        when(event.getSource()).thenReturn(source);
        when(source.getUserId()).thenReturn("user-audio");
        when(audioContent.getId()).thenReturn("audio-msg-id");

        service.dispatchEvents(List.of(event), lineGfService);

        verify(lineGfService).handleAudio("token-audio", "audio-msg-id", "user-audio");
    }

    @Test
    @DisplayName("dispatchEvents 遇 AudioMessageContent 應分發給 ILineDiaryService")
    void dispatchEvents_ShouldDispatchAudioMessage_ToDiaryService() {
        MessageEvent<AudioMessageContent> event = mock(MessageEvent.class);
        AudioMessageContent audioContent = mock(AudioMessageContent.class);

        when(event.getMessage()).thenReturn(audioContent);
        when(event.getReplyToken()).thenReturn("token-audio-diary");
        when(audioContent.getId()).thenReturn("audio-msg-diary-id");

        service.dispatchEvents(List.of(event), lineDiaryService);

        verify(lineDiaryService).handleAudio("token-audio-diary", "audio-msg-diary-id");
    }

    @Test
    @DisplayName("dispatchEvents 遇 null MessageContent 不應拋出 NPE 且安全略過")
    void dispatchEvents_ShouldHandleNullMessageContent_WithoutNpe() {
        MessageEvent<MessageContent> event = mock(MessageEvent.class);
        when(event.getMessage()).thenReturn(null);
        when(event.getReplyToken()).thenReturn("token-null");

        assertDoesNotThrow(() -> service.dispatchEvents(List.of(event), lineGfService));
        verifyNoInteractions(lineGfService);
    }

    @Test
    @DisplayName("dispatchEvents 遇未支援之 MessageContent 型別應安全略過")
    void dispatchEvents_ShouldIgnoreUnsupportedMessageContent() {
        MessageEvent<ImageMessageContent> event = mock(MessageEvent.class);
        ImageMessageContent imageContent = mock(ImageMessageContent.class);

        when(event.getMessage()).thenReturn(imageContent);
        when(event.getReplyToken()).thenReturn("token-image");

        assertDoesNotThrow(() -> service.dispatchEvents(List.of(event), lineGfService));
        verifyNoInteractions(lineGfService);
    }

    @Test
    @DisplayName("dispatchEvents 遇非 MessageEvent 應直接略過")
    void dispatchEvents_ShouldIgnoreNonMessageEvents() {
        FollowEvent followEvent = mock(FollowEvent.class);

        assertDoesNotThrow(() -> service.dispatchEvents(List.of(followEvent), lineGfService));
        verifyNoInteractions(lineGfService);
    }

    @Test
    @DisplayName("dispatchEvents 遇 null 或空白 userId 時應略過 ILineGfService 避免 Session 污染")
    void dispatchEvents_ShouldIgnoreGfEvents_whenUserIdNullOrBlank() {
        MessageEvent<TextMessageContent> textEventNullSrc = mock(MessageEvent.class);
        TextMessageContent textContent = mock(TextMessageContent.class);
        when(textEventNullSrc.getMessage()).thenReturn(textContent);
        when(textEventNullSrc.getReplyToken()).thenReturn("token-no-src");
        when(textEventNullSrc.getSource()).thenReturn(null);
        when(textContent.getText()).thenReturn("Hello without source");

        MessageEvent<AudioMessageContent> audioEventNullSrc = mock(MessageEvent.class);
        AudioMessageContent audioContent = mock(AudioMessageContent.class);
        when(audioEventNullSrc.getMessage()).thenReturn(audioContent);
        when(audioEventNullSrc.getReplyToken()).thenReturn("token-audio-no-src");
        when(audioEventNullSrc.getSource()).thenReturn(null);
        when(audioContent.getId()).thenReturn("audio-id-no-src");

        assertDoesNotThrow(() -> service.dispatchEvents(List.of(textEventNullSrc, audioEventNullSrc), lineGfService));
        verifyNoInteractions(lineGfService);

        // 但對於不需要 userId 的 ILineDiaryService 仍應正常分發
        assertDoesNotThrow(() -> service.dispatchEvents(List.of(textEventNullSrc, audioEventNullSrc), lineDiaryService));
        verify(lineDiaryService).handleText("token-no-src", "Hello without source");
        verify(lineDiaryService).handleAudio("token-audio-no-src", "audio-id-no-src");
    }
}
