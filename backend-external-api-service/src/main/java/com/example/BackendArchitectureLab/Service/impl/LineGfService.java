package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Entity.LineGfSession;
import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Repository.LineGfSessionRepository;
import com.example.BackendArchitectureLab.Service.ILineGfService;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import com.example.BackendArchitectureLab.Vo.ChatRequestVo;
import com.example.BackendArchitectureLab.Vo.ChatResponseVo;
import com.example.BackendArchitectureLab.Vo.SttResponseVo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.client.LineBlobClient;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.message.TextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private IUsageTrackService usageTrackService;

    @Autowired
    private LineGfSessionRepository sessionRepository;

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
        } else if ("#設定說話語音".equals(text)) {
            replyText(replyToken, "LINE 不支援上傳語音樣本，請使用 Discord 進行語音設定");
        } else {
            replyText(replyToken, "未知指令，請輸入 #幫助 查看可用指令");
        }
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
                #啟用語音 - 啟用語音回覆
                #關閉語音 - 關閉語音回覆
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

        List<Map<String, String>> messages = new ArrayList<>();
        if (session.getPrompt() != null) {
            messages.add(Map.of("role", "system", "content", session.getPrompt()));
        }
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

        if (Boolean.TRUE.equals(session.getVoiceEnabled())) {
            replyText(replyToken, reply + "\n（語音模式已啟用）");
        } else {
            replyText(replyToken, reply);
        }
    }

    @Override
    public void handleAudio(String replyToken, String messageId) {
        usageTrackService.track("line-gf", "stt", "file", 1L);
        try {
            MessageContentResponse content = blobClient.getMessageContent(messageId).get();
            byte[] audioBytes = content.getStream().readAllBytes();
            content.close();

            SttResponseVo stt = aiPyServiceFeignClient.recognize(audioBytes, "zh");
            String text = stt.getText() != null ? stt.getText() : "無法辨識";
            messagingClient.replyMessage(new ReplyMessage(replyToken, new TextMessage("你說：" + text))).join();
        } catch (Exception e) {
            messagingClient.replyMessage(new ReplyMessage(replyToken, new TextMessage("辨識失敗：" + e.getMessage()))).join();
        }
    }

    private void replyText(String replyToken, String text) {
        messagingClient.replyMessage(new ReplyMessage(replyToken, new TextMessage(text))).join();
    }
}
