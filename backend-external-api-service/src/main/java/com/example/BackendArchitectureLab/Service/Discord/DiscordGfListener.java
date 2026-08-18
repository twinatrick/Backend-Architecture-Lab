package com.example.BackendArchitectureLab.Service.Discord;

import com.example.BackendArchitectureLab.Entity.DiscordGfSession;
import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Repository.DiscordGfSessionRepository;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import com.example.BackendArchitectureLab.Service.ITtsService;
import com.example.BackendArchitectureLab.Service.ISttService;
import com.example.BackendArchitectureLab.Service.Impl.TtsService;
import com.example.BackendArchitectureLab.Vo.ChatRequestVo;
import com.example.BackendArchitectureLab.Vo.ChatResponseVo;
import com.example.BackendArchitectureLab.Vo.TtsRequestVo;
import com.example.BackendArchitectureLab.Vo.TtsResponseVo;
import com.example.BackendArchitectureLab.Mapper.GfSessionMapper;
import com.example.BackendArchitectureLab.Vo.DiscordGfSessionVo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class DiscordGfListener extends ListenerAdapter {

    private final DiscordGfSessionRepository sessionRepository;
    private final GfSessionMapper gfSessionMapper;
    private final AiPyServiceFeignClient aiPyServiceFeignClient;
    private final IUsageTrackService usageTrackService;
    private final MinioClient minioClient;
    private final ITtsService ttsService;
    private final ISttService sttService;
    private final ObjectMapper objectMapper;

    @Value("${minio.bucket}")
    private String bucket;

    private final ConcurrentHashMap<String, Webhook> webhookCache = new ConcurrentHashMap<>();

    private static final List<CommandData> COMMANDS = List.of(
            Commands.slash("啟用女友對話", "啟用或關閉女友對話模式"),
            Commands.slash("女友提示詞", "設定女友角色提示詞")
                    .addOption(OptionType.STRING, "內容", "提示詞內容", true),
            Commands.slash("女友名稱", "設定女友顯示名稱")
                    .addOption(OptionType.STRING, "名稱", "女友的名稱", true),
            Commands.slash("女友頭像", "設定女友頭像")
                    .addOption(OptionType.STRING, "網址", "頭像圖片 URL", true),
            Commands.slash("啟用語音", "啟用語音回覆功能"),
            Commands.slash("關閉語音", "關閉語音回覆功能"),
            Commands.slash("設定說話語音", "上傳語音樣本設定女友聲音")
                    .addOption(OptionType.STRING, "台詞", "音檔中說的台詞文字", true)
                    .addOption(OptionType.ATTACHMENT, "音檔", "上傳語音檔案", true),
            Commands.slash("女友語言", "設定女友說話與聽取的語言")
                    .addOption(OptionType.STRING, "語言", "選擇語言 (zh/ja/en)", true),
            Commands.slash("狀態", "查看當前女友模式設定")
    );

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        event.getGuild().updateCommands().addCommands(COMMANDS).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String channelId = event.getChannel().getId();
        String userId = event.getUser().getId();

        switch (event.getName()) {
            case "啟用女友對話" -> handleToggleGf(event, channelId, userId);
            case "女友提示詞" -> handleSetPrompt(event, channelId, userId);
            case "啟用語音" -> handleVoiceOn(event, channelId, userId);
            case "關閉語音" -> handleVoiceOff(event, channelId, userId);
            case "設定說話語音" -> handleSetVoice(event, channelId, userId);
            case "女友名稱" -> handleSetGfName(event, channelId, userId);
            case "女友頭像" -> handleSetGfAvatar(event, channelId, userId);
            case "女友語言" -> handleSetLanguage(event, channelId, userId);
            case "狀態" -> handleStatus(event, channelId, userId);
        }
    }

    private void handleToggleGf(SlashCommandInteractionEvent event, String channelId, String userId) {
        DiscordGfSession session = sessionRepository.findByChannelIdAndUserId(channelId, userId)
                .orElse(new DiscordGfSession());
        if (Boolean.TRUE.equals(session.getActive())) {
            session.setActive(false);
            sessionRepository.save(session);
            event.reply("已關閉你的女友對話模式").setEphemeral(true).queue();
        } else {
            String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
            session.setGuildId(guildId);
            session.setChannelId(channelId);
            session.setUserId(userId);
            session.setActive(true);
            if (session.getPrompt() == null) {
                session.setPrompt("你是一個可愛的女朋友，用溫柔關心的語氣回覆");
            }
            sessionRepository.save(session);
            event.reply("已啟用你的女友對話模式！今後你在這個頻道傳送的非指令訊息都會得到女友回覆。").setEphemeral(true).queue();
        }
    }

    private void handleSetPrompt(SlashCommandInteractionEvent event, String channelId, String userId) {
        var option = event.getOption("內容");
        if (option == null) {
            event.reply("❌ 請提供提示詞內容").setEphemeral(true).queue();
            return;
        }
        String prompt = option.getAsString();
        DiscordGfSession session = sessionRepository.findByChannelIdAndUserId(channelId, userId)
                .orElse(new DiscordGfSession());
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        session.setGuildId(guildId);
        session.setChannelId(channelId);
        session.setUserId(userId);
        session.setPrompt(prompt);
        session.setConversationHistory(null);
        sessionRepository.save(session);
        event.reply("已設定你的女友提示詞").setEphemeral(true).queue();
    }

    private void handleVoiceOn(SlashCommandInteractionEvent event, String channelId, String userId) {
        DiscordGfSession session = sessionRepository.findByChannelIdAndUserId(channelId, userId)
                .orElse(new DiscordGfSession());
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        session.setGuildId(guildId);
        session.setChannelId(channelId);
        session.setUserId(userId);
        session.setVoiceEnabled(true);
        sessionRepository.save(session);
        event.reply("已啟用你的語音回覆").setEphemeral(true).queue();
    }

    private void handleVoiceOff(SlashCommandInteractionEvent event, String channelId, String userId) {
        sessionRepository.findByChannelIdAndUserId(channelId, userId).ifPresent(s -> {
            s.setVoiceEnabled(false);
            sessionRepository.save(s);
        });
        event.reply("已關閉你的語音回覆").setEphemeral(true).queue();
    }

    private void handleSetLanguage(SlashCommandInteractionEvent event, String channelId, String userId) {
        var option = event.getOption("語言");
        if (option == null) {
            event.reply("❌ 請提供語言").setEphemeral(true).queue();
            return;
        }
        String lang = option.getAsString().toLowerCase();
        if (!List.of("zh", "ja", "en").contains(lang)) {
            event.reply("❌ 不支援的語言。僅支援: zh (繁中), ja (日文), en (英文)").setEphemeral(true).queue();
            return;
        }
        DiscordGfSession session = sessionRepository.findByChannelIdAndUserId(channelId, userId)
                .orElse(new DiscordGfSession());
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        session.setGuildId(guildId);
        session.setChannelId(channelId);
        session.setUserId(userId);
        session.setLanguage(lang);
        sessionRepository.save(session);
        event.reply("✅ 已將女友語言設定為：" + lang).setEphemeral(true).queue();
    }

    private void handleSetVoice(SlashCommandInteractionEvent event, String channelId, String userId) {
        var textOption = event.getOption("台詞");
        var fileOption = event.getOption("音檔");
        if (textOption == null || fileOption == null) {
            event.reply("❌ 請提供台詞與音檔").setEphemeral(true).queue();
            return;
        }
        String text = textOption.getAsString();
        Message.Attachment attachment = fileOption.getAsAttachment();

        event.deferReply(true).queue();

        attachment.getProxy().download().thenAcceptAsync(inputStream -> {
            try {
                byte[] audioBytes = inputStream.readAllBytes();
                String objectKey = "tts-refs/" + userId + "/current.wav";

                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(new ByteArrayInputStream(audioBytes), audioBytes.length, -1)
                        .contentType(attachment.getContentType())
                        .build());

                DiscordGfSession session = sessionRepository.findByChannelIdAndUserId(channelId, userId)
                        .orElse(new DiscordGfSession());
                String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
                session.setGuildId(guildId);
                session.setChannelId(channelId);
                session.setUserId(userId);
                session.setVoiceSampleKey(objectKey);
                session.setVoiceSampleText(text);
                sessionRepository.save(session);

                event.getHook().sendMessage("已設定你的語音樣本（台詞：" + text + "）").queue();
            } catch (Exception e) {
                event.getHook().sendMessage("設定語音樣本失敗：" + e.getMessage()).queue();
            }
        });
    }

    private void handleStatus(SlashCommandInteractionEvent event, String channelId, String userId) {
        Optional<DiscordGfSession> opt = sessionRepository.findByChannelIdAndUserId(channelId, userId);
        if (opt.isEmpty()) {
            event.reply("❌ 你尚未設定女友對話模式").setEphemeral(true).queue();
            return;
        }
        DiscordGfSession s = opt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("**女友模式**：").append(Boolean.TRUE.equals(s.getActive()) ? "✅ 啟用" : "❌ 關閉").append("\n");
        sb.append("**提示詞**：").append(s.getPrompt() != null ? s.getPrompt().substring(0, Math.min(50, s.getPrompt().length())) + "..." : "未設定").append("\n");
        sb.append("**女友名稱**：").append(s.getGfName() != null ? s.getGfName() : "預設").append("\n");
        sb.append("**女友頭像**：").append(s.getGfAvatarUrl() != null ? "✅ 已設定" : "❌ 未設定").append("\n");
        sb.append("**語音回覆**：").append(Boolean.TRUE.equals(s.getVoiceEnabled()) ? "✅ 啟用" : "❌ 關閉").append("\n");
        sb.append("**女友語言**：").append(s.getLanguage() != null ? s.getLanguage() : "zh").append("\n");
        sb.append("**語音樣本**：").append(s.getVoiceSampleKey() != null ? "✅ 已設定" : "❌ 未設定").append("\n");
        if (s.getConversationHistory() != null) {
            try {
                List<Map<String, Object>> hist = objectMapper.readValue(s.getConversationHistory(), new TypeReference<List<Map<String, Object>>>() {});
                sb.append("**對話歷史**：").append(hist.size()).append(" 則");
            } catch (Exception e) {
                sb.append("**對話歷史**：讀取失敗");
            }
        } else {
            sb.append("**對話歷史**：無");
        }
        event.reply(sb.toString()).setEphemeral(true).queue();
    }

    private void handleSetGfName(SlashCommandInteractionEvent event, String channelId, String userId) {
        var option = event.getOption("名稱");
        if (option == null) {
            event.reply("❌ 請提供名稱").setEphemeral(true).queue();
            return;
        }
        String name = option.getAsString();
        DiscordGfSession session = sessionRepository.findByChannelIdAndUserId(channelId, userId)
                .orElse(new DiscordGfSession());
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        session.setGuildId(guildId);
        session.setChannelId(channelId);
        session.setUserId(userId);
        session.setGfName(name);
        sessionRepository.save(session);
        event.reply("✅ 已設定女友名稱：" + name).setEphemeral(true).queue();
    }

    private void handleSetGfAvatar(SlashCommandInteractionEvent event, String channelId, String userId) {
        var option = event.getOption("網址");
        if (option == null) {
            event.reply("❌ 請提供網址").setEphemeral(true).queue();
            return;
        }
        String url = option.getAsString();
        DiscordGfSession session = sessionRepository.findByChannelIdAndUserId(channelId, userId)
                .orElse(new DiscordGfSession());
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        session.setGuildId(guildId);
        session.setChannelId(channelId);
        session.setUserId(userId);
        session.setGfAvatarUrl(url);
        sessionRepository.save(session);
        event.reply("✅ 已設定女友頭像").setEphemeral(true).queue();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (event.isWebhookMessage()) return;

        String channelId = event.getChannel().getId();
        String userId = event.getAuthor().getId();
        Optional<DiscordGfSession> sessionOpt = sessionRepository.findByChannelIdAndUserId(channelId, userId);
        if (sessionOpt.isEmpty() || !Boolean.TRUE.equals(sessionOpt.get().getActive())) return;

        DiscordGfSession session = sessionOpt.get();
        String userName = event.getMember() != null ? event.getMember().getEffectiveName() : event.getAuthor().getName();

        // 1. 處理語音附件接收
        if (!event.getMessage().getAttachments().isEmpty()) {
            var attachment = event.getMessage().getAttachments().getFirst();
            String ct = attachment.getContentType();
            if (ct != null && ct.startsWith("audio/")) {
                attachment.getProxy().download().thenAcceptAsync(inputStream -> {
                    try {
                        byte[] audioBytes = inputStream.readAllBytes();
                        String lang = session.getLanguage() != null ? session.getLanguage() : "zh";

                        // 呼叫共用高階處理
                        String text = sttService.recognizeAndTrack(audioBytes, lang, "discord-gf");

                        if (text.isEmpty()) {
                            event.getChannel().sendMessage("（聽不清楚你說什麼...）").queue();
                            return;
                        }

                        // 直接非同步呼叫對話處理大腦！
                        processChat(event, session, text, userName);
                    } catch (Exception e) {
                        e.printStackTrace();
                        event.getChannel().sendMessage("（聽取語音失敗）").queue();
                    }
                });
                return;
            }
        }

        // 2. 處理文字對話
        String content = event.getMessage().getContentRaw();
        if (content == null || content.isBlank()) return;
        if (content.startsWith("/")) return;

        processChat(event, session, content, userName);
    }

    private void processChat(MessageReceivedEvent event, DiscordGfSession session, String content, String userName) {
        String userId = event.getAuthor().getId();
        TypeReference<Map<String, List<Map<String, String>>>> mapTypeRef = new TypeReference<>() {};
        Map<String, List<Map<String, String>>> allHistories = new HashMap<>();
        if (session.getConversationHistory() != null) {
            try {
                allHistories = objectMapper.readValue(session.getConversationHistory(), mapTypeRef);
            } catch (Exception e) {
                allHistories = new HashMap<>();
            }
        }

        List<Map<String, String>> userHistory = allHistories.getOrDefault(userId, new ArrayList<>());

        DiscordGfSessionVo sessionVo = gfSessionMapper.toVo(session);
        List<Map<String, String>> messages = sessionVo.buildSystemMessage();
        messages.addAll(userHistory);
        messages.add(Map.of("role", "user", "content", content, "name", userName));

        ChatRequestVo chatRequest = new ChatRequestVo(messages, null, false);
        ChatResponseVo chatResponse = aiPyServiceFeignClient.chat(chatRequest);
        usageTrackService.track("discord-gf", "chat", "char", (long) content.length());

        String reply = chatResponse.getContent();

        userHistory.add(Map.of("role", "user", "content", content, "name", userName));
        userHistory.add(Map.of("role", "assistant", "content", reply));
        if (userHistory.size() > 20) {
            userHistory = userHistory.subList(userHistory.size() - 20, userHistory.size());
        }
        allHistories.put(userId, userHistory);
        try {
            session.setConversationHistory(objectMapper.writeValueAsString(allHistories));
        } catch (Exception e) {
            session.setConversationHistory(null);
        }
        sessionRepository.save(session);

        if (Boolean.TRUE.equals(session.getVoiceEnabled())) {
            try {
                String lang = session.getLanguage() != null ? session.getLanguage() : "zh";

                // 套用動作過濾：只將純台詞傳送給語音合成，避免尷尬旁白讀出，且大幅加速合成
                String speechText = TtsService.filterActionsForTts(reply);
                if (speechText.isEmpty()) {
                    speechText = reply; // 如果全為動作描述，則回退使用原本的回覆
                }

                TtsRequestVo ttsRequest = TtsRequestVo.builder()
                        .text(speechText)
                        .language(lang)
                        .voiceSampleKey(session.getVoiceSampleKey())
                        .voiceSampleText(session.getVoiceSampleText())
                        .voiceSampleLang(lang)
                        .build();
                TtsResponseVo ttsResponse = aiPyServiceFeignClient.synthesize(ttsRequest);
                String audioUrl = ttsResponse.getAudioUrl();

                byte[] audioBytes = ttsService.downloadAudio(audioUrl);
                sendReply(event, session, " ", audioBytes);
            } catch (Exception e) {
                e.printStackTrace();
                sendReply(event, session, reply + "\n（語音合成失敗）", null);
            }
        } else {
            sendReply(event, session, reply, null);
        }
    }

    private void sendReply(MessageReceivedEvent event, DiscordGfSession session, String text, byte[] audioBytes) {
        String gfName = session.getGfName();
        String gfAvatarUrl = session.getGfAvatarUrl();

        if (gfName == null && gfAvatarUrl == null) {
            sendDirect(event, text, audioBytes);
            return;
        }

        if (!(event.getChannel() instanceof TextChannel textChannel)) {
            sendDirect(event, text, audioBytes);
            return;
        }

        String cid = textChannel.getId();
        Webhook cached = webhookCache.get(cid);
        if (cached != null) {
            sendViaWebhook(cached, text, gfName, gfAvatarUrl, audioBytes);
            return;
        }

        textChannel.retrieveWebhooks().queue(webhooks -> {
            Webhook hook = webhooks.stream()
                    .filter(w -> w.getOwner() != null && w.getOwner().getIdLong() == event.getJDA().getSelfUser().getIdLong())
                    .findFirst()
                    .orElse(null);
            if (hook != null) {
                webhookCache.put(cid, hook);
                sendViaWebhook(hook, text, gfName, gfAvatarUrl, audioBytes);
            } else {
                textChannel.createWebhook("女友").queue(h -> {
                    webhookCache.put(cid, h);
                    sendViaWebhook(h, text, gfName, gfAvatarUrl, audioBytes);
                }, error -> sendDirect(event, text, audioBytes));
            }
        }, error -> sendDirect(event, text, audioBytes));
    }

    private void sendViaWebhook(Webhook webhook, String text, String gfName, String gfAvatarUrl, byte[] audioBytes) {
        var action = webhook.sendMessage(text);
        if (gfName != null) action = action.setUsername(gfName);
        if (gfAvatarUrl != null) action = action.setAvatarUrl(gfAvatarUrl);
        if (audioBytes != null) action = action.addFiles(FileUpload.fromData(audioBytes, "reply.wav"));
        action.queue();
    }

    private void sendDirect(MessageReceivedEvent event, String text, byte[] audioBytes) {
        if (audioBytes != null) {
            event.getChannel().sendMessage(text)
                    .addFiles(FileUpload.fromData(audioBytes, "reply.wav"))
                    .queue();
        } else {
            event.getChannel().sendMessage(text).queue();
        }
    }
}
