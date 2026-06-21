package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.SttResponseVo;
import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Service.ILineDiaryService;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import com.linecorp.bot.client.LineBlobClient;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.message.TextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class LineDiaryService implements ILineDiaryService {

    @Autowired(required = false)
    @Qualifier("diaryLineMessagingClient")
    private LineMessagingClient messagingClient;

    @Autowired(required = false)
    @Qualifier("diaryLineBlobClient")
    private LineBlobClient blobClient;

    @Autowired
    private AiPyServiceFeignClient aiPyServiceFeignClient;

    @Autowired
    private IUsageTrackService usageTrackService;

    @Override
    public void handleText(String replyToken, String text) {
        usageTrackService.track("line-diary", "chat", "char", (long) text.length());
        if (messagingClient == null) {
            throw new IllegalStateException("Diary LINE bot not configured");
        }
        messagingClient.replyMessage(new ReplyMessage(replyToken, new TextMessage("已收到：" + text))).join();
    }

    @Override
    public void handleAudio(String replyToken, String messageId) {
        usageTrackService.track("line-diary", "stt", "file", 1L);
        if (messagingClient == null || blobClient == null) {
            throw new IllegalStateException("Diary LINE bot not configured");
        }
        try {
            MessageContentResponse content = blobClient.getMessageContent(messageId).get();
            byte[] audioBytes = content.getStream().readAllBytes();
            content.close();

            SttResponseVo stt = aiPyServiceFeignClient.recognize(audioBytes, "zh");
            String text = stt.getText() != null ? stt.getText() : "無法辨識";
            messagingClient.replyMessage(new ReplyMessage(replyToken, new TextMessage("語音內容：" + text))).join();
        } catch (Exception e) {
            messagingClient.replyMessage(new ReplyMessage(replyToken, new TextMessage("辨識失敗：" + e.getMessage()))).join();
        }
    }
}
