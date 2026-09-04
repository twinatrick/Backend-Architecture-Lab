package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.ILineDiaryService;
import com.example.BackendArchitectureLab.Service.ISttService;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import com.linecorp.bot.client.LineBlobClient;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.message.TextMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LineDiaryService implements ILineDiaryService {

    @Qualifier("diaryLineMessagingClient")
    private final Optional<LineMessagingClient> messagingClient;

    @Qualifier("diaryLineBlobClient")
    private final Optional<LineBlobClient> blobClient;

    private final ISttService sttService;
    private final IUsageTrackService usageTrackService;

    @Override
    public void handleText(String replyToken, String text) {
        usageTrackService.track("line-diary", "chat", "char", (long) text.length());
        LineMessagingClient client = messagingClient.orElseThrow(() -> new IllegalStateException("Diary LINE bot not configured"));
        client.replyMessage(new ReplyMessage(replyToken, new TextMessage("已收到：" + text))).join();
    }

    @Override
    public void handleAudio(String replyToken, String messageId) {
        LineMessagingClient msgClient = messagingClient.orElseThrow(() -> new IllegalStateException("Diary LINE bot not configured"));
        LineBlobClient bClient = blobClient.orElseThrow(() -> new IllegalStateException("Diary LINE bot not configured"));
        try {
            MessageContentResponse content = bClient.getMessageContent(messageId).get();
            byte[] audioBytes = content.getStream().readAllBytes();
            content.close();

            // 呼叫共用高階處理 (日記預設 zh)
            String text = sttService.recognizeAndTrack(audioBytes, "zh", "line-diary");
            if (text.isEmpty()) {
                msgClient.replyMessage(new ReplyMessage(replyToken, new TextMessage("（聽不清楚你說什麼...）"))).join();
                return;
            }
            msgClient.replyMessage(new ReplyMessage(replyToken, new TextMessage("語音內容：" + text))).join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            msgClient.replyMessage(new ReplyMessage(replyToken, new TextMessage("辨識中斷：" + e.getMessage()))).join();
        } catch (Exception e) {
            msgClient.replyMessage(new ReplyMessage(replyToken, new TextMessage("辨識失敗：" + e.getMessage()))).join();
        }
    }
}
