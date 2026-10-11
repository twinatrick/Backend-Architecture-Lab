package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IDiscordGfSessionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IDiscordSubscriptionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ILineGfSessionDataAccess;
import com.example.BackendArchitectureLab.Service.IExternalPlatformStatusService;
import com.example.BackendArchitectureLab.Vo.AiPlatformStatusVo;
import com.example.BackendArchitectureLab.Vo.DiscordPlatformStatusVo;
import com.example.BackendArchitectureLab.Vo.ExternalPlatformStatusVo;
import com.example.BackendArchitectureLab.Vo.LinePlatformStatusVo;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExternalPlatformStatusService implements IExternalPlatformStatusService {

    private final IDiscordGfSessionDataAccess discordGfSessionDataAccess;
    private final IDiscordSubscriptionDataAccess discordSubscriptionDataAccess;
    private final ILineGfSessionDataAccess lineGfSessionDataAccess;
    private final ApplicationContext applicationContext;

    @Value("${discord.gf.token:}")
    private String discordGfToken;

    @Value("${discord.diary.token:}")
    private String discordDiaryToken;

    @Value("${line.gf.channel-token:}")
    private String lineGfToken;

    @Value("${line.gf.channel-secret:}")
    private String lineGfSecret;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${deepseek.api.key:}")
    private String deepseekApiKey;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${github.models.api.key:}")
    private String githubApiKey;

    @Override
    public ExternalPlatformStatusVo getPlatformStatus() {
        return ExternalPlatformStatusVo.builder()
                .discord(probeDiscordStatus())
                .line(probeLineStatus())
                .ai(probeAiStatus())
                .build();
    }

    private DiscordPlatformStatusVo probeDiscordStatus() {
        boolean configured = (discordGfToken != null && !discordGfToken.isBlank())
                || (discordDiaryToken != null && !discordDiaryToken.isBlank());

        String gfStatus = "NOT_CONFIGURED";
        Long gfPing = null;
        if (applicationContext.containsBean("discordGfJda")) {
            try {
                JDA jda = applicationContext.getBean("discordGfJda", JDA.class);
                gfStatus = jda.getStatus() == JDA.Status.CONNECTED ? "ONLINE" : jda.getStatus().name();
                gfPing = jda.getGatewayPing();
            } catch (Exception e) {
                gfStatus = "OFFLINE";
            }
        }

        String diaryStatus = "NOT_CONFIGURED";
        Long diaryPing = null;
        if (applicationContext.containsBean("discordDiaryJda")) {
            try {
                JDA jda = applicationContext.getBean("discordDiaryJda", JDA.class);
                diaryStatus = jda.getStatus() == JDA.Status.CONNECTED ? "ONLINE" : jda.getStatus().name();
                diaryPing = jda.getGatewayPing();
            } catch (Exception e) {
                diaryStatus = "OFFLINE";
            }
        }

        long sessionCount = 0;
        try {
            sessionCount = discordGfSessionDataAccess.findAll().size();
        } catch (Exception ignored) {
        }

        long subscriptionCount = 0;
        try {
            subscriptionCount = discordSubscriptionDataAccess.findAll().size();
        } catch (Exception ignored) {
        }

        return DiscordPlatformStatusVo.builder()
                .configured(configured)
                .gfStatus(gfStatus)
                .diaryStatus(diaryStatus)
                .gfPing(gfPing)
                .diaryPing(diaryPing)
                .sessionCount(sessionCount)
                .subscriptionCount(subscriptionCount)
                .build();
    }

    private LinePlatformStatusVo probeLineStatus() {
        boolean tokenConfigured = lineGfToken != null && !lineGfToken.isBlank();
        boolean secretConfigured = lineGfSecret != null && !lineGfSecret.isBlank();
        boolean configured = tokenConfigured && secretConfigured;

        long sessionCount = 0;
        try {
            sessionCount = lineGfSessionDataAccess.findAll().size();
        } catch (Exception ignored) {
        }

        return LinePlatformStatusVo.builder()
                .configured(configured)
                .channelTokenConfigured(tokenConfigured)
                .channelSecretConfigured(secretConfigured)
                .sessionCount(sessionCount)
                .build();
    }

    private AiPlatformStatusVo probeAiStatus() {
        String provider = "gemini";
        String modelName = "gemini-3.7-flash";
        boolean configured = false;
        boolean apiKeyConfigured = false;

        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            provider = "gemini";
            modelName = "gemini-3.7-flash";
            apiKeyConfigured = true;
            configured = true;
        } else if (deepseekApiKey != null && !deepseekApiKey.isBlank()) {
            provider = "deepseek";
            modelName = "deepseek-v4-flash";
            apiKeyConfigured = true;
            configured = true;
        } else if (groqApiKey != null && !groqApiKey.isBlank()) {
            provider = "groq";
            modelName = "llama-3.3-70b-versatile";
            apiKeyConfigured = true;
            configured = true;
        } else if (githubApiKey != null && !githubApiKey.isBlank()) {
            provider = "github";
            modelName = "gpt-4o-mini";
            apiKeyConfigured = true;
            configured = true;
        }

        return AiPlatformStatusVo.builder()
                .configured(configured)
                .provider(provider)
                .apiKeyConfigured(apiKeyConfigured)
                .modelName(modelName)
                .build();
    }
}
