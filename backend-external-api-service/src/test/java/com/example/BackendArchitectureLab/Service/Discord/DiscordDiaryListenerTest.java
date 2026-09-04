package com.example.BackendArchitectureLab.Service.Discord;

import com.example.BackendArchitectureLab.Entity.DiscordSubscription;
import com.example.BackendArchitectureLab.Repository.DiscordSubscriptionRepository;
import com.example.BackendArchitectureLab.Service.ISttService;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscordDiaryListenerTest {

    @Mock
    private DiscordSubscriptionRepository subscriptionRepository;

    @Mock
    private ISttService sttService;

    @Mock
    private DiscordWebhookNotifier webhookNotifier;

    @Mock
    private IUsageTrackService usageTrackService;

    @Mock
    private MessageReceivedEvent event;

    @Mock
    private User author;

    @Mock
    private Message message;

    @Mock
    private Guild guild;

    @Mock
    private MessageChannelUnion channel;

    @Mock
    private MessageCreateAction messageCreateAction;

    @InjectMocks
    private DiscordDiaryListener listener;

    @BeforeEach
    void setUp() {
        lenient().when(event.getAuthor()).thenReturn(author);
    }

    @Test
    void onMessageReceived_whenAuthorIsBot_shouldReturnImmediately() {
        when(author.isBot()).thenReturn(true);

        listener.onMessageReceived(event);

        verify(event, never()).isFromGuild();
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void onMessageReceived_whenNotFromGuild_shouldReturnImmediately() {
        when(author.isBot()).thenReturn(false);
        when(event.isFromGuild()).thenReturn(false);

        listener.onMessageReceived(event);

        verify(event, never()).getMessage();
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void onMessageReceived_whenDiaryCommandAndAlreadySubscribed_shouldNotifyExisting() {
        when(author.isBot()).thenReturn(false);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getMessage()).thenReturn(message);
        when(message.getContentRaw()).thenReturn("/日記");
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("guild-1");
        when(event.getChannel()).thenReturn(channel);
        when(channel.getId()).thenReturn("chan-1");
        when(channel.sendMessage(anyString())).thenReturn(messageCreateAction);

        DiscordSubscription sub = new DiscordSubscription("guild-1", "chan-1", "diary");
        when(subscriptionRepository.findByGuildIdAndBotType("guild-1", "diary")).thenReturn(Optional.of(sub));

        listener.onMessageReceived(event);

        verify(channel).sendMessage("此伺服器已啟用日記功能");
        verify(messageCreateAction).queue();
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void onMessageReceived_whenRemoveCommand_shouldDeleteSubscription() {
        when(author.isBot()).thenReturn(false);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getMessage()).thenReturn(message);
        when(message.getContentRaw()).thenReturn("/移除");
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("guild-1");
        when(event.getChannel()).thenReturn(channel);
        when(channel.getId()).thenReturn("chan-1");
        when(channel.sendMessage(anyString())).thenReturn(messageCreateAction);

        DiscordSubscription sub = new DiscordSubscription("guild-1", "chan-1", "diary");
        when(subscriptionRepository.findByGuildIdAndBotType("guild-1", "diary")).thenReturn(Optional.of(sub));

        listener.onMessageReceived(event);

        verify(subscriptionRepository).deleteByGuildIdAndBotType("guild-1", "diary");
        verify(channel).sendMessage("已移除日記功能");
        verify(messageCreateAction).queue();
    }

    @Test
    void onMessageReceived_whenAttachmentIsNotAudio_shouldReturnEarly() {
        when(author.isBot()).thenReturn(false);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getMessage()).thenReturn(message);
        when(message.getContentRaw()).thenReturn("hello");
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("guild-1");
        when(event.getChannel()).thenReturn(channel);
        when(channel.getId()).thenReturn("chan-1");

        Message.Attachment attachment = mock(Message.Attachment.class);
        when(attachment.getContentType()).thenReturn("image/png");
        when(message.getAttachments()).thenReturn(List.of(attachment));

        DiscordSubscription sub = new DiscordSubscription("guild-1", "chan-1", "diary");
        when(subscriptionRepository.findByGuildIdAndBotType("guild-1", "diary")).thenReturn(Optional.of(sub));

        listener.onMessageReceived(event);

        verifyNoInteractions(sttService);
    }
}
