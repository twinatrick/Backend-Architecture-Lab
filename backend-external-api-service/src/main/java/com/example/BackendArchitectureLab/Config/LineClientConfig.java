package com.example.BackendArchitectureLab.Config;

import com.linecorp.bot.client.LineBlobClient;
import com.linecorp.bot.client.LineMessagingClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LineClientConfig {

    @Bean
    @Qualifier("gfLineMessagingClient")
    public LineMessagingClient gfLineMessagingClient(
            @Value("${line.gf.channel-token:}") String token) {
        if (token.isBlank()) return null;
        return LineMessagingClient.builder(token).build();
    }

    @Bean
    @Qualifier("gfLineBlobClient")
    public LineBlobClient gfLineBlobClient(
            @Value("${line.gf.channel-token:}") String token) {
        if (token.isBlank()) return null;
        return LineBlobClient.builder(token).build();
    }

    @Bean
    @Qualifier("diaryLineMessagingClient")
    public LineMessagingClient diaryLineMessagingClient(
            @Value("${line.diary.channel-token:}") String token) {
        if (token.isBlank()) return null;
        return LineMessagingClient.builder(token).build();
    }

    @Bean
    @Qualifier("diaryLineBlobClient")
    public LineBlobClient diaryLineBlobClient(
            @Value("${line.diary.channel-token:}") String token) {
        if (token.isBlank()) return null;
        return LineBlobClient.builder(token).build();
    }
}
