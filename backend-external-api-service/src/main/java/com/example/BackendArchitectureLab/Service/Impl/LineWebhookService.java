package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.ILineDiaryService;
import com.example.BackendArchitectureLab.Service.ILineGfService;
import com.example.BackendArchitectureLab.Service.ILineWebhookService;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.AudioMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.parser.LineSignatureValidator;
import com.linecorp.bot.parser.WebhookParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class LineWebhookService implements ILineWebhookService {

    private static final Logger log = LoggerFactory.getLogger(LineWebhookService.class);

    @Override
    public List<Event> parseEvents(String channelSecret, byte[] bodyBytes, String signature) throws Exception {
        LineSignatureValidator validator = new LineSignatureValidator(channelSecret.getBytes(StandardCharsets.UTF_8));
        WebhookParser parser = new WebhookParser(validator);
        return parser.handle(signature, bodyBytes).getEvents();
    }

    @Override
    public void dispatchEvents(List<Event> events, Object service) {
        for (Event event : events) {
            if (!(event instanceof MessageEvent<?> msgEvent)) continue;
            String replyToken = msgEvent.getReplyToken();

            switch (msgEvent.getMessage()) {
                case null -> log.debug("忽略沒有 message content 的 LINE event");
                case TextMessageContent textContent -> {
                    String text = textContent.getText();
                    if (service instanceof ILineGfService gfService) {
                        String userId = msgEvent.getSource().getUserId();
                        gfService.handleText(replyToken, text, userId);
                    } else if (service instanceof ILineDiaryService diaryService) {
                        diaryService.handleText(replyToken, text);
                    }
                }
                case AudioMessageContent audioContent -> {
                    String messageId = audioContent.getId();
                    if (service instanceof ILineGfService gfService) {
                        String userId = msgEvent.getSource().getUserId();
                        gfService.handleAudio(replyToken, messageId, userId);
                    } else if (service instanceof ILineDiaryService diaryService) {
                        diaryService.handleAudio(replyToken, messageId);
                    }
                }
                default -> log.debug("忽略未支援的 LINE 訊息型別: {}", msgEvent.getMessage().getClass().getSimpleName());
            }
        }
    }
}
