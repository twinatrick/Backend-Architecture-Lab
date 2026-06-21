package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Service.ILineDiaryService;
import com.example.BackendArchitectureLab.Service.ILineGfService;
import com.example.BackendArchitectureLab.Service.ILineWebhookService;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.AudioMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.parser.LineSignatureValidator;
import com.linecorp.bot.parser.WebhookParser;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

@Service
public class LineWebhookService implements ILineWebhookService {

    @Override
    public List<Event> parseEvents(String channelSecret, String body, String signature) throws Exception {
        WebhookParser parser = new WebhookParser(new LineSignatureValidator(channelSecret.getBytes()));
        byte[] signatureBytes = Base64.getDecoder().decode(signature);
        return parser.handle(body, signatureBytes).getEvents();
    }

    @Override
    public void dispatchEvents(List<Event> events, Object service) {
        for (Event event : events) {
            if (!(event instanceof MessageEvent)) continue;
            MessageEvent<?> msgEvent = (MessageEvent<?>) event;
            String replyToken = msgEvent.getReplyToken();

            if (msgEvent.getMessage() instanceof TextMessageContent) {
                String text = ((TextMessageContent) msgEvent.getMessage()).getText();
                if (service instanceof ILineGfService) {
                    ((ILineGfService) service).handleText(replyToken, text);
                } else if (service instanceof ILineDiaryService) {
                    ((ILineDiaryService) service).handleText(replyToken, text);
                }
            } else if (msgEvent.getMessage() instanceof AudioMessageContent) {
                String messageId = ((AudioMessageContent) msgEvent.getMessage()).getId();
                if (service instanceof ILineGfService) {
                    ((ILineGfService) service).handleAudio(replyToken, messageId);
                } else if (service instanceof ILineDiaryService) {
                    ((ILineDiaryService) service).handleAudio(replyToken, messageId);
                }
            }
        }
    }
}
