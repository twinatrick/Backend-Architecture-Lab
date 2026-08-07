package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.DataAccess.ILineGfSessionDataAccess;
import com.example.BackendArchitectureLab.Entity.LineGfSession;
import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Service.ILineGfService;
import com.example.BackendArchitectureLab.Service.ITtsService;
import com.example.BackendArchitectureLab.Service.ISttService;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import java.net.URI;
import com.example.BackendArchitectureLab.Vo.ChatRequestVo;
import com.example.BackendArchitectureLab.Vo.ChatResponseVo;
import com.example.BackendArchitectureLab.Vo.SttResponseVo;
import com.example.BackendArchitectureLab.Vo.TtsRequestVo;
import com.example.BackendArchitectureLab.Vo.TtsResponseVo;
import com.example.BackendArchitectureLab.Mapper.GfSessionMapper;
import com.example.BackendArchitectureLab.Vo.LineGfSessionVo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.client.LineBlobClient;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.message.AudioMessage;
import com.linecorp.bot.model.message.TextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LineGfService implements ILineGfService {

    @Autowired
    @Qualifier("gfLineMessagingClient")
    private LineMessagingClient messagingClient;

    @Autowired
    @Qualifier("gfLineBlobClient")
    private LineBlobClient blobClient;

    @Autowired
    private AiPyServiceFeignClient aiPyServiceFeignClient;

    @Autowired
    private ITtsService ttsService;

    @Autowired
    private ISttService sttService;

    @Autowired
    private IUsageTrackService usageTrackService;

    @Autowired
    private ILineGfSessionDataAccess sessionRepository;

    @Autowired
    private GfSessionMapper gfSessionMapper;

    @Value("${OUT_URL:}")
    private String outUrl;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void handleText(String replyToken, String text, String userId) {
        if (text.startsWith("#")) {
            handleCommand(replyToken, text, userId);
            return;
        }

        Optional<LineGfSession> sessionOpt = sessionRepository.findByUserId(userId);
        if (sessionOpt.isEmpty()) return;
        LineGfSession session = sessionOpt.get();

        if (Boolean.TRUE.equals(session.getPendingPrompt())) {
            session.setPrompt(text);
            session.setPendingPrompt(false);
            session.setConversationHistory(null);
            sessionRepository.save(session);
            replyText(replyToken, "✅ 已設定提示詞");
            return;
        }

        if (Boolean.TRUE.equals(session.getActive())) {
            handleAiChat(replyToken, text, userId, session);
        }
    }

    private void handleCommand(String replyToken, String text, String userId) {
        if ("#啟用女友".equals(text)) {
            toggleGf(replyToken, userId, true);
        } else if ("#關閉女友".equals(text)) {
            toggleGf(replyToken, userId, false);
        } else if ("#啟用語音".equals(text)) {
            setVoiceEnabled(replyToken, userId, true);
        } else if ("#關閉語音".equals(text)) {
            setVoiceEnabled(replyToken, userId, false);
        } else if ("#狀態".equals(text)) {
            showStatus(replyToken, userId);
        } else if ("#幫助".equals(text)) {
            showHelp(replyToken);
        } else if (text.startsWith("#提示詞 ")) {
            setPrompt(replyToken, userId, text.substring(4).trim());
        } else if ("#提示詞".equals(text)) {
            LineGfSession session = sessionRepository.findByUserId(userId).orElse(new LineGfSession());
            session.setUserId(userId);
            session.setPendingPrompt(true);
            sessionRepository.save(session);
            replyText(replyToken, "請輸入提示詞內容，直接傳送即可：");
        } else if (text.startsWith("#女友名稱 ")) {
            setGfName(replyToken, userId, text.substring(5).trim());
        } else if (text.startsWith("#女友頭像 ")) {
            setGfAvatar(replyToken, userId, text.substring(5).trim());
        } else if (text.startsWith("#語言 ")) {
            setLanguage(replyToken, userId, text.substring(4).trim().toLowerCase());
        } else if ("#設定說話語音".equals(text)) {
            replyText(replyToken, "LINE 不支援上傳語音樣本，請使用 Discord 進行語音設定");
        } else {
            replyText(replyToken, "未知指令，請輸入 #幫助 查看可用指令");
        }
    }

    private void setLanguage(String replyToken, String userId, String lang) {
        if (!List.of("zh", "ja", "en").contains(lang)) {
            replyText(replyToken, "❌ 不支援的語言。目前僅支援: zh (繁中), ja (日文), en (英文)");
            return;
        }
        LineGfSession session = sessionRepository.findByUserId(userId).orElse(new LineGfSession());
        session.setUserId(userId);
        session.setLanguage(lang);
        sessionRepository.save(session);
        replyText(replyToken, "✅ 已將女友語言設定為：" + lang);
    }

    private void toggleGf(String replyToken, String userId, boolean enable) {
        LineGfSession session = sessionRepository.findByUserId(userId).orElse(new LineGfSession());
        session.setUserId(userId);
        session.setActive(enable);
        if (enable && session.getPrompt() == null) {
            session.setPrompt("你是一個可愛的女朋友，用溫柔關心的語氣回覆");
        }
        sessionRepository.save(session);
        replyText(replyToken, enable ? "✅ 已啟用女友對話模式" : "❌ 已關閉女友對話模式");
    }

    private void setVoiceEnabled(String replyToken, String userId, boolean enable) {
        LineGfSession session = sessionRepository.findByUserId(userId).orElse(new LineGfSession());
        session.setUserId(userId);
        session.setVoiceEnabled(enable);
        sessionRepository.save(session);
        replyText(replyToken, enable ? "✅ 已啟用語音回覆（文字回覆）" : "❌ 已關閉語音回覆");
    }

    private void setPrompt(String replyToken, String userId, String prompt) {
        LineGfSession session = sessionRepository.findByUserId(userId).orElse(new LineGfSession());
        session.setUserId(userId);
        session.setPrompt(prompt);
        session.setConversationHistory(null);
        sessionRepository.save(session);
        replyText(replyToken, "✅ 已設定提示詞");
    }

    private void setGfName(String replyToken, String userId, String gfName) {
        LineGfSession session = sessionRepository.findByUserId(userId).orElse(new LineGfSession());
        session.setUserId(userId);
        session.setGfName(gfName);
        sessionRepository.save(session);
        replyText(replyToken, "✅ 已設定女友名稱：" + gfName);
    }

    private void setGfAvatar(String replyToken, String userId, String gfAvatarUrl) {
        LineGfSession session = sessionRepository.findByUserId(userId).orElse(new LineGfSession());
        session.setUserId(userId);
        session.setGfAvatarUrl(gfAvatarUrl);
        sessionRepository.save(session);
        replyText(replyToken, "✅ 已設定女友頭像（LINE 僅記錄，無法實際更改顯示圖片）");
    }

    private void showStatus(String replyToken, String userId) {
        Optional<LineGfSession> opt = sessionRepository.findByUserId(userId);
        if (opt.isEmpty()) {
            replyText(replyToken, "❌ 尚未設定女友對話模式\n請輸入 #啟用女友 開始使用");
            return;
        }
        LineGfSession s = opt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("女友模式：").append(Boolean.TRUE.equals(s.getActive()) ? "✅ 啟用" : "❌ 關閉").append("\n");
        sb.append("語音回覆：").append(Boolean.TRUE.equals(s.getVoiceEnabled()) ? "✅ 啟用" : "❌ 關閉").append("\n");
        sb.append("女友語言：").append(s.getLanguage() != null ? s.getLanguage() : "zh").append("\n");
        sb.append("女友名稱：").append(s.getGfName() != null ? s.getGfName() : "預設").append("\n");
        sb.append("提示詞：").append(s.getPrompt() != null ? s.getPrompt().substring(0, Math.min(30, s.getPrompt().length())) + "..." : "未設定").append("\n");
        if (s.getConversationHistory() != null) {
            try {
                Map<String, List<Map<String, String>>> hist = objectMapper.readValue(s.getConversationHistory(), new TypeReference<Map<String, List<Map<String, String>>>>() {});
                sb.append("對話歷史：").append(hist.values().stream().mapToInt(List::size).sum()).append(" 則");
            } catch (Exception e) {
                sb.append("對話歷史：讀取失敗");
            }
        } else {
            sb.append("對話歷史：無");
        }
        replyText(replyToken, sb.toString());
    }

    private void showHelp(String replyToken) {
        replyText(replyToken, """
                可用指令：
                #啟用女友 - 啟用對話模式
                #關閉女友 - 關閉對話模式
                #提示詞 [內容] - 設定角色提示詞
                #女友名稱 [名稱] - 設定女友名稱
                #女友頭像 [網址] - 設定女友頭像網址
                #啟用語音 - 啟用語音回覆
                #關閉語音 - 關閉語音回覆
                #語言 [zh/ja/en] - 設定女友語言
                #狀態 - 查看目前設定
                #幫助 - 顯示此訊息
                """);
    }

    private void handleAiChat(String replyToken, String text, String userId, LineGfSession session) {
        usageTrackService.track("line-gf", "chat", "char", (long) text.length());

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

        LineGfSessionVo sessionVo = gfSessionMapper.toVo(session);
        List<Map<String, String>> messages = sessionVo.buildSystemMessage();
        messages.addAll(userHistory);
        messages.add(Map.of("role", "user", "content", text));

        ChatRequestVo chatRequest = new ChatRequestVo(messages, null, false);
        ChatResponseVo chatResponse = aiPyServiceFeignClient.chat(chatRequest);

        String reply = chatResponse.getContent();

        userHistory.add(Map.of("role", "user", "content", text));
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

        // boolean voiceEnable = Boolean.TRUE.equals(session.getVoiceEnabled());
        boolean voiceEnable = false; // 暫時全面關閉 LINE 語音回復（TTS 發送），避開免費版 ngrok 攔截警告。未來只需將此行改回即可一秒還原。

        if (voiceEnable) {
            try {
                String lang = sessionVo.getLanguage() != null ? sessionVo.getLanguage() : "zh";
                
                // 套用動作過濾：只將純台詞傳送給語音合成，避免尷尬旁白讀出，且大幅加速合成
                String speechText = TtsService.filterActionsForTts(reply);
                if (speechText.isEmpty()) {
                    speechText = reply; // 如果全為動作描述，則回退使用原本的回覆
                }

                TtsRequestVo ttsRequest = TtsRequestVo.builder()
                        .text(speechText)
                        .language(lang)
                        .voiceSampleKey(sessionVo.getVoiceSampleKey())
                        .voiceSampleText(sessionVo.getVoiceSampleText())
                        .voiceSampleLang(lang)
                        .build();
                 TtsResponseVo ttsResponse = aiPyServiceFeignClient.synthesize(ttsRequest);
                 String audioUrl = ttsResponse.getAudioUrl();
                 String presignedUrl;
                 if (outUrl != null && !outUrl.isBlank()) {
                     String fileName = audioUrl.substring(audioUrl.lastIndexOf("/") + 1);
                     presignedUrl = outUrl.trim() + "/external/public/audio/stream/" + fileName;
                 } else {
                     presignedUrl = ttsService.getPresignedUrl(audioUrl);
                 }
                
                // 動態估算音檔毫秒長度（字數 * 300毫秒，最低 2000毫秒，以純台詞長度為準）
                Long duration = Long.valueOf(Math.max(2000, speechText.length() * 300));

                messagingClient.replyMessage(new ReplyMessage(replyToken, List.of(
                        new TextMessage(reply),
                        new AudioMessage(URI.create(presignedUrl), duration)
                ))).join();
            } catch (Exception e) {
                e.printStackTrace();
                messagingClient.replyMessage(new ReplyMessage(replyToken, new TextMessage(reply + "\n（語音合成失敗）"))).join();
            }
        } else {
            replyText(replyToken, reply);
        }
    }

    @Override
    public void handleAudio(String replyToken, String messageId, String userId) {
        try {
            MessageContentResponse content = blobClient.getMessageContent(messageId).get();
            byte[] audioBytes = content.getStream().readAllBytes();
            content.close();

            LineGfSession session = sessionRepository.findByUserId(userId).orElse(new LineGfSession());
            String lang = session.getLanguage() != null ? session.getLanguage() : "zh";

            // 呼叫共用高階處理
            String text = sttService.recognizeAndTrack(audioBytes, lang, "line-gf");
            if (text.isEmpty()) {
                replyText(replyToken, "（聽不清楚你說什麼...）");
                return;
            }

            // 直接呼叫 handleText，啟動 AI 思考與語音回覆！
            handleText(replyToken, text, userId);
        } catch (Exception e) {
            e.printStackTrace();
            replyText(replyToken, "聽取語音失敗：" + e.getMessage());
        }
    }

    private void replyText(String replyToken, String text) {
        messagingClient.replyMessage(new ReplyMessage(replyToken, new TextMessage(text))).join();
    }
}
