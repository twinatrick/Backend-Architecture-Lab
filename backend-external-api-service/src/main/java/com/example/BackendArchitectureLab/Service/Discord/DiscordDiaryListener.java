package com.example.BackendArchitectureLab.Service.Discord;

import com.example.BackendArchitectureLab.Vo.SttResponseVo;
import com.example.BackendArchitectureLab.Entity.DiscordSubscription;
import com.example.BackendArchitectureLab.Repository.DiscordSubscriptionRepository;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import com.example.BackendArchitectureLab.Service.ISttService;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DiscordDiaryListener extends ListenerAdapter {

    @Autowired
    private DiscordSubscriptionRepository subscriptionRepository;

    @Autowired
    private ISttService sttService;

    @Autowired
    private DiscordWebhookNotifier webhookNotifier;

    @Autowired
    private IUsageTrackService usageTrackService;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw();
        String guildId = event.getGuild().getId();
        String channelId = event.getChannel().getId();

        if (content.equals("/日記")) {
            Optional<DiscordSubscription> existing = subscriptionRepository
                    .findByGuildIdAndBotType(guildId, "diary");
            if (existing.isPresent()) {
                event.getChannel().sendMessage("此伺服器已啟用日記功能").queue();
                return;
            }
            DiscordSubscription sub = new DiscordSubscription(guildId, channelId, "diary");
            if (event.getChannel() instanceof TextChannel textChannel) {
                textChannel.createWebhook("Diary Bot").queue(webhook -> {
                    sub.setWebhookUrl(webhook.getUrl());
                    sub.setWebhookId(webhook.getId());
                    subscriptionRepository.save(sub);
                }, error -> subscriptionRepository.save(sub));
            } else {
                subscriptionRepository.save(sub);
            }
            event.getChannel().sendMessage("已啟用日記功能，語音訊息將自動辨識為日記內容").queue();
            return;
        }

        if (content.equals("/移除")) {
            Optional<DiscordSubscription> sub = subscriptionRepository
                    .findByGuildIdAndBotType(guildId, "diary");
            sub.ifPresent(s -> {
                if (s.getWebhookId() != null && event.getChannel() instanceof TextChannel textChannel) {
                    textChannel.retrieveWebhooks().queue(webhooks ->
                            webhooks.stream()
                                    .filter(w -> w.getId().equals(s.getWebhookId()))
                                    .findFirst()
                                    .ifPresent(w -> w.delete().queue()));
                }
            });
            subscriptionRepository.deleteByGuildIdAndBotType(guildId, "diary");
            event.getChannel().sendMessage("已移除日記功能").queue();
            return;
        }

        if (!event.getMessage().getAttachments().isEmpty()) {
            Optional<DiscordSubscription> sub = subscriptionRepository
                    .findByGuildIdAndBotType(guildId, "diary");
            if (sub.isEmpty()) return;

            var attachment = event.getMessage().getAttachments().get(0);
            String ct = attachment.getContentType();
            if (ct == null || !ct.startsWith("audio/")) return;

            event.getChannel().sendMessage("語音辨識中，請稍後..").queue();

            attachment.getProxy().download().thenAcceptAsync(inputStream -> {
                try {
                    byte[] audioBytes = inputStream.readAllBytes();
                    
                    // 呼叫共用高階處理 (日記預設 zh)
                    String text = sttService.recognizeAndTrack(audioBytes, "zh", "discord-diary");
                    if (text.isEmpty()) {
                        event.getChannel().sendMessage("（聽不清楚你說什麼...）").queue();
                        return;
                    }

                    String webhookUrl = sub.get().getWebhookUrl();
                    if (webhookUrl != null) {
                        webhookNotifier.send(webhookUrl, "語音內容：" + text, event.getAuthor().getName());
                    }
                    event.getChannel().sendMessage("已記錄到日記：" + text).queue();
                } catch (Exception e) {
                    event.getChannel().sendMessage("語音辨識失敗：" + e.getMessage()).queue();
                }
            });
        }
    }
}
