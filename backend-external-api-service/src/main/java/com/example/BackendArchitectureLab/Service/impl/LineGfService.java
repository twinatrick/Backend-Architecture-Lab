package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Vo.SttResponseVo;
import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Service.ILineGfService;
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

    @Override
    public void handleText(String replyToken, String text) {
        usageTrackService.track("line-gf", "chat", "char", (long) text.length());
        messagingClient.replyMessage(new ReplyMessage(replyToken, new TextMessage("女友說：" + text))).join();
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
}
