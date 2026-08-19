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
    @DisplayName("dispatchEvents 遇 null Source 應安全傳遞 null userId 給 ILineGfService")
    void dispatchEvents_ShouldHandleNullSource_Safely() {
        MessageEvent<TextMessageContent> textEvent = mock(MessageEvent.class);
        TextMessageContent textContent = mock(TextMessageContent.class);
        when(textEvent.getMessage()).thenReturn(textContent);
        when(textEvent.getReplyToken()).thenReturn("token-no-src");
        when(textEvent.getSource()).thenReturn(null);
        when(textContent.getText()).thenReturn("Hello without source");

        MessageEvent<AudioMessageContent> audioEvent = mock(MessageEvent.class);
        AudioMessageContent audioContent = mock(AudioMessageContent.class);
        when(audioEvent.getMessage()).thenReturn(audioContent);
        when(audioEvent.getReplyToken()).thenReturn("token-audio-no-src");
        when(audioEvent.getSource()).thenReturn(null);
        when(audioContent.getId()).thenReturn("audio-id-no-src");

        assertDoesNotThrow(() -> service.dispatchEvents(List.of(textEvent, audioEvent), lineGfService));

        verify(lineGfService).handleText("token-no-src", "Hello without source", null);
        verify(lineGfService).handleAudio("token-audio-no-src", "audio-id-no-src", null);
    }
}
