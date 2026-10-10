package com.example.BackendArchitectureLab.Service.Discord;

import com.example.BackendArchitectureLab.DataAccess.IDiscordGfMessageDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IDiscordGfSessionDataAccess;
import com.example.BackendArchitectureLab.Entity.DiscordGfMessage;
import com.example.BackendArchitectureLab.Entity.DiscordGfSession;
import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Mapper.GfSessionMapper;
import com.example.BackendArchitectureLab.Service.ISttService;
import com.example.BackendArchitectureLab.Service.ITtsService;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import com.example.BackendArchitectureLab.Vo.ChatRequestVo;
import com.example.BackendArchitectureLab.Vo.ChatResponseVo;
import com.example.BackendArchitectureLab.Vo.DiscordGfSessionVo;
import io.minio.MinioClient;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscordGfListener 單元測試")
class DiscordGfListenerTest {

    @Mock
    private IDiscordGfSessionDataAccess sessionDataAccess;

    @Mock
    private IDiscordGfMessageDataAccess messageDataAccess;

    @Mock
    private GfSessionMapper gfSessionMapper;

    @Mock
    private AiPyServiceFeignClient aiPyServiceFeignClient;

    @Mock
    private IUsageTrackService usageTrackService;

    @Mock
    private MinioClient minioClient;

    @Mock
    private ITtsService ttsService;

    @Mock
    private ISttService sttService;

    @Mock
    private MessageReceivedEvent messageReceivedEvent;

    @Mock
    private SlashCommandInteractionEvent slashEvent;

    @Mock
    private User author;

    @Mock
    private MessageChannelUnion channelUnion;

    @Mock
    private Message message;

    @Mock
    private MessageCreateAction messageCreateAction;

    @Mock
    private ReplyCallbackAction replyCallbackAction;

    private DiscordGfListener listener;

    @BeforeEach
    void setUp() {
        listener = new DiscordGfListener(
                sessionDataAccess,
                messageDataAccess,
                gfSessionMapper,
                aiPyServiceFeignClient,
                usageTrackService,
                minioClient,
                ttsService,
                sttService
        );
    }

    @Test
    @DisplayName("onMessageReceived 遇 Bot 訊息應直接略過")
    void onMessageReceived_WhenAuthorIsBot_ShouldIgnore() {
        when(messageReceivedEvent.getAuthor()).thenReturn(author);
        when(author.isBot()).thenReturn(true);

        listener.onMessageReceived(messageReceivedEvent);

        verifyNoInteractions(sessionDataAccess, messageDataAccess, aiPyServiceFeignClient);
    }

    @Test
    @DisplayName("onMessageReceived 遇非 Bot 且啟用女友對話時應保存 1:N 訊息並呼叫 AI 回覆")
    void onMessageReceived_WhenActiveSession_ShouldPersistMessagesAndReply() {
        UUID sessionId = UUID.randomUUID();
        DiscordGfSession session = new DiscordGfSession();
        session.setChannelId("ch-100");
        session.setUserId("u-100");
        session.setActive(true);
        session.setGfName("小茜");
        session.setId(sessionId);

        when(messageReceivedEvent.getAuthor()).thenReturn(author);
        when(author.isBot()).thenReturn(false);
        when(messageReceivedEvent.isWebhookMessage()).thenReturn(false);
        when(messageReceivedEvent.getChannel()).thenReturn(channelUnion);
        when(channelUnion.getId()).thenReturn("ch-100");
        when(author.getId()).thenReturn("u-100");
        when(author.getName()).thenReturn("Gary");
        when(messageReceivedEvent.getMessage()).thenReturn(message);
        when(message.getAttachments()).thenReturn(Collections.emptyList());
        when(message.getContentRaw()).thenReturn("你好呀");

        when(sessionDataAccess.findByChannelIdAndUserId("ch-100", "u-100")).thenReturn(Optional.of(session));
        when(gfSessionMapper.toVo(session)).thenReturn(DiscordGfSessionVo.builder().gfName("小茜").build());
        when(messageDataAccess.findRecentMessages(sessionId, 20)).thenReturn(Collections.emptyList());
        when(aiPyServiceFeignClient.chat(any(ChatRequestVo.class))).thenReturn(ChatResponseVo.builder().content("哈囉！今天過得好嗎？").build());

        when(channelUnion.sendMessage(anyString())).thenReturn(messageCreateAction);

        listener.onMessageReceived(messageReceivedEvent);

        ArgumentCaptor<DiscordGfMessage> msgCaptor = ArgumentCaptor.forClass(DiscordGfMessage.class);
        verify(messageDataAccess, times(2)).save(msgCaptor.capture());

        List<DiscordGfMessage> saved = msgCaptor.getAllValues();
        assertEquals("user", saved.get(0).getRole());
        assertEquals("你好呀", saved.get(0).getContent());
        assertEquals("Gary", saved.get(0).getSenderName());
        assertEquals(sessionId, saved.get(0).getSessionId());

        assertEquals("assistant", saved.get(1).getRole());
        assertEquals("哈囉！今天過得好嗎？", saved.get(1).getContent());
        assertEquals("小茜", saved.get(1).getSenderName());
        assertEquals(sessionId, saved.get(1).getSessionId());

        verify(usageTrackService).track("discord-gf", "chat", "char", 3L);
    }

    @Test
    @DisplayName("onSlashCommandInteraction 當名稱為 狀態 時應透過 messageDataAccess 統計訊息總數")
    void onSlashCommandInteraction_Status_ShouldCountMessagesViaMessageDataAccess() {
        UUID sessionId = UUID.randomUUID();
        DiscordGfSession session = new DiscordGfSession();
        session.setChannelId("ch-200");
        session.setUserId("u-200");
        session.setActive(true);
        session.setId(sessionId);

        when(slashEvent.getChannel()).thenReturn(channelUnion);
        when(channelUnion.getId()).thenReturn("ch-200");
        when(slashEvent.getUser()).thenReturn(author);
        when(author.getId()).thenReturn("u-200");
        when(slashEvent.getName()).thenReturn("狀態");
        when(sessionDataAccess.findByChannelIdAndUserId("ch-200", "u-200")).thenReturn(Optional.of(session));
        when(messageDataAccess.countBySessionId(sessionId)).thenReturn(42L);

        when(slashEvent.reply(anyString())).thenReturn(replyCallbackAction);
        when(replyCallbackAction.setEphemeral(true)).thenReturn(replyCallbackAction);

        listener.onSlashCommandInteraction(slashEvent);

        verify(messageDataAccess).countBySessionId(sessionId);
        verify(slashEvent).reply(anyString());
    }
}
