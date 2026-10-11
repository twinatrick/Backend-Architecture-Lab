package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IDiscordGfSessionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IDiscordSubscriptionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ILineGfSessionDataAccess;
import com.example.BackendArchitectureLab.Entity.DiscordGfSession;
import com.example.BackendArchitectureLab.Entity.DiscordSubscription;
import com.example.BackendArchitectureLab.Entity.LineGfSession;
import com.example.BackendArchitectureLab.Vo.ExternalPlatformStatusVo;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalPlatformStatusService 單元測試")
class ExternalPlatformStatusServiceTest {

    @Mock
    private IDiscordGfSessionDataAccess discordGfSessionDataAccess;

    @Mock
    private IDiscordSubscriptionDataAccess discordSubscriptionDataAccess;

    @Mock
    private ILineGfSessionDataAccess lineGfSessionDataAccess;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private JDA discordGfJda;

    @Mock
    private JDA discordDiaryJda;

    private ExternalPlatformStatusService createService(
            String discordGfToken,
            String discordDiaryToken,
            String lineGfToken,
            String lineGfSecret,
            String geminiApiKey,
            String deepseekApiKey,
            String groqApiKey,
            String githubApiKey) {
        ExternalPlatformStatusService service = new ExternalPlatformStatusService(
                discordGfSessionDataAccess,
                discordSubscriptionDataAccess,
                lineGfSessionDataAccess,
                applicationContext
        );
        ReflectionTestUtils.setField(service, "discordGfToken", discordGfToken);
        ReflectionTestUtils.setField(service, "discordDiaryToken", discordDiaryToken);
        ReflectionTestUtils.setField(service, "lineGfToken", lineGfToken);
        ReflectionTestUtils.setField(service, "lineGfSecret", lineGfSecret);
        ReflectionTestUtils.setField(service, "geminiApiKey", geminiApiKey);
        ReflectionTestUtils.setField(service, "deepseekApiKey", deepseekApiKey);
        ReflectionTestUtils.setField(service, "groqApiKey", groqApiKey);
        ReflectionTestUtils.setField(service, "githubApiKey", githubApiKey);
        return service;
    }

    @Test
    @DisplayName("平台正常配置且 JDA 連線成功時應回傳 ONLINE 與統計數據")
    void getPlatformStatus_WhenConfiguredAndOnline() {
        ExternalPlatformStatusService service = createService(
                "token-gf",
                "token-diary",
                "line-token",
                "line-secret",
                "gemini-key",
                "",
                "",
                ""
        );

        when(applicationContext.containsBean("discordGfJda")).thenReturn(true);
        when(applicationContext.getBean("discordGfJda", JDA.class)).thenReturn(discordGfJda);
        when(discordGfJda.getStatus()).thenReturn(JDA.Status.CONNECTED);
        when(discordGfJda.getGatewayPing()).thenReturn(45L);

        when(applicationContext.containsBean("discordDiaryJda")).thenReturn(true);
        when(applicationContext.getBean("discordDiaryJda", JDA.class)).thenReturn(discordDiaryJda);
        when(discordDiaryJda.getStatus()).thenReturn(JDA.Status.CONNECTED);
        when(discordDiaryJda.getGatewayPing()).thenReturn(50L);

        when(discordGfSessionDataAccess.findAll()).thenReturn(List.of(new DiscordGfSession()));
        when(discordSubscriptionDataAccess.findAll()).thenReturn(List.of(new DiscordSubscription()));
        when(lineGfSessionDataAccess.findAll()).thenReturn(List.of(new LineGfSession()));

        ExternalPlatformStatusVo status = service.getPlatformStatus();

        assertNotNull(status);
        assertTrue(status.getDiscord().isConfigured());
        assertEquals("ONLINE", status.getDiscord().getGfStatus());
        assertEquals(45L, status.getDiscord().getGfPing());
        assertEquals("ONLINE", status.getDiscord().getDiaryStatus());
        assertEquals(50L, status.getDiscord().getDiaryPing());
        assertEquals(1, status.getDiscord().getSessionCount());
        assertEquals(1, status.getDiscord().getSubscriptionCount());

        assertTrue(status.getLine().isConfigured());
        assertTrue(status.getLine().isChannelTokenConfigured());
        assertTrue(status.getLine().isChannelSecretConfigured());
        assertEquals(1, status.getLine().getSessionCount());

        assertTrue(status.getAi().isConfigured());
        assertEquals("gemini", status.getAi().getProvider());
        assertTrue(status.getAi().isApiKeyConfigured());
    }

    @Test
    @DisplayName("JDA 連線探查拋出例外時應 Fail-Safe 降級為 OFFLINE")
    void getPlatformStatus_WhenJdaThrows_ShouldFallbackToOffline() {
        ExternalPlatformStatusService service = createService(
                "token-gf",
                "",
                "",
                "",
                "",
                "",
                "",
                ""
        );

        when(applicationContext.containsBean("discordGfJda")).thenReturn(true);
        when(applicationContext.getBean("discordGfJda", JDA.class)).thenThrow(new RuntimeException("JDA error"));

        ExternalPlatformStatusVo status = service.getPlatformStatus();

        assertNotNull(status);
        assertEquals("OFFLINE", status.getDiscord().getGfStatus());
        assertEquals("NOT_CONFIGURED", status.getDiscord().getDiaryStatus());
        assertNull(status.getDiscord().getGfPing());
    }

    @Test
    @DisplayName("未配置憑證時應回傳 NOT_CONFIGURED 與 false")
    void getPlatformStatus_WhenNotConfigured() {
        ExternalPlatformStatusService service = createService(
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                ""
        );

        when(applicationContext.containsBean("discordGfJda")).thenReturn(false);
        when(applicationContext.containsBean("discordDiaryJda")).thenReturn(false);

        ExternalPlatformStatusVo status = service.getPlatformStatus();

        assertNotNull(status);
        assertFalse(status.getDiscord().isConfigured());
        assertEquals("NOT_CONFIGURED", status.getDiscord().getGfStatus());
        assertEquals("NOT_CONFIGURED", status.getDiscord().getDiaryStatus());
        assertFalse(status.getLine().isConfigured());
        assertFalse(status.getAi().isConfigured());
    }

    @Test
    @DisplayName("配置 DeepSeek 時應正確反映 provider 與 model")
    void getPlatformStatus_WithDeepSeek() {
        ExternalPlatformStatusService service = createService(
                "",
                "",
                "",
                "",
                "",
                "deepseek-key",
                "",
                ""
        );

        ExternalPlatformStatusVo status = service.getPlatformStatus();

        assertTrue(status.getAi().isConfigured());
        assertEquals("deepseek", status.getAi().getProvider());
        assertEquals("deepseek-v4-flash", status.getAi().getModelName());
    }

    @Test
    @DisplayName("配置 Groq 時應正確反映 provider 與 model")
    void getPlatformStatus_WithGroq() {
        ExternalPlatformStatusService service = createService(
                "",
                "",
                "",
                "",
                "",
                "",
                "groq-key",
                ""
        );

        ExternalPlatformStatusVo status = service.getPlatformStatus();

        assertTrue(status.getAi().isConfigured());
        assertEquals("groq", status.getAi().getProvider());
        assertEquals("llama-3.3-70b-versatile", status.getAi().getModelName());
    }

    @Test
    @DisplayName("配置 GitHub Models 時應正確反映 provider 與 model")
    void getPlatformStatus_WithGitHubModels() {
        ExternalPlatformStatusService service = createService(
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "gh-key"
        );

        ExternalPlatformStatusVo status = service.getPlatformStatus();

        assertTrue(status.getAi().isConfigured());
        assertEquals("github", status.getAi().getProvider());
        assertEquals("gpt-4o-mini", status.getAi().getModelName());
    }
}
