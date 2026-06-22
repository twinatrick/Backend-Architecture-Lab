package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Service.Discord.DiscordDiaryListener;
import com.example.BackendArchitectureLab.Service.Discord.DiscordGfListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiscordBotConfig {

    @Bean
    @ConditionalOnExpression("!'${discord.gf.token:}'.isEmpty()")
    public JDA discordGfJda(@Value("${discord.gf.token}") String token,
                            DiscordGfListener listener) {
        return JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES)
                .addEventListeners(listener)
                .build();
    }

    @Bean
    @ConditionalOnExpression("!'${discord.diary.token:}'.isEmpty()")
    public JDA discordDiaryJda(@Value("${discord.diary.token}") String token,
                               DiscordDiaryListener listener) {
        return JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES)
                .addEventListeners(listener)
                .build();
    }
}
