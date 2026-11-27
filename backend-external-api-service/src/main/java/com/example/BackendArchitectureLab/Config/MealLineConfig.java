package com.example.BackendArchitectureLab.Config;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.message.TextMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MealLineConfig {
    @Bean
    public MealLineClient mealLineClient(MealProperties properties) {
        return new SdkMealLineClient(properties);
    }

    static final class SdkMealLineClient implements MealLineClient {
        private final MealProperties properties;
        private volatile LineMessagingClient client;

        SdkMealLineClient(MealProperties properties) {
            this.properties = properties;
        }

        @Override
        public boolean isConfigured() {
            return properties.getLineChannelToken() != null && !properties.getLineChannelToken().isBlank()
                    && properties.getLineChannelSecret() != null && !properties.getLineChannelSecret().isBlank();
        }

        private LineMessagingClient client() {
            if (!isConfigured()) {
                throw new IllegalStateException("飲食專用 LINE Bot 尚未啟用或未設定憑證");
            }
            LineMessagingClient current = client;
            if (current == null) {
                current = LineMessagingClient.builder(properties.getLineChannelToken()).build();
                client = current;
            }
            return current;
        }

        @Override
        public void replyText(String replyToken, String text) {
            if (replyToken == null || replyToken.isBlank()) {
                return;
            }
            client().replyMessage(new ReplyMessage(replyToken, new TextMessage(text))).join();
        }

        @Override
        public void pushText(String lineUserId, String text) {
            client().pushMessage(new PushMessage(lineUserId, new TextMessage(text))).join();
        }
    }
}
