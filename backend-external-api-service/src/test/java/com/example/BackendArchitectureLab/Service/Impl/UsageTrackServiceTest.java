package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Config.BotConfigLoader;
import com.example.BackendArchitectureLab.DataAccess.IApiUsageLogDataAccess;
import com.example.BackendArchitectureLab.Entity.ApiUsageLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsageTrackService 單元測試")
class UsageTrackServiceTest {

    @Mock
    private IApiUsageLogDataAccess apiUsageLogDataAccess;

    @Mock
    private BotConfigLoader botConfigLoader;

    private UsageTrackService service;

    @BeforeEach
    void setUp() {
        service = new UsageTrackService(apiUsageLogDataAccess, botConfigLoader);
    }

    @Test
    @DisplayName("track 特定服務代碼有專屬設定時應優先使用該上限")
    void track_ShouldUseDirectServiceLimitWhenConfigured() {
        when(botConfigLoader.get("discord-gf", "cost_limit_daily")).thenReturn("10.0");
        when(apiUsageLogDataAccess.findByServiceAndCreatedTimeBetween(eq("discord-gf"), any(), any()))
                .thenReturn(Collections.emptyList());

        boolean result = service.track("discord-gf", "chat", "char", 100L);

        assertTrue(result);
        ArgumentCaptor<ApiUsageLog> captor = ArgumentCaptor.forClass(ApiUsageLog.class);
        verify(apiUsageLogDataAccess).save(captor.capture());
        assertEquals("discord-gf", captor.getValue().getService());
        assertEquals("chat", captor.getValue().getCallType());
        assertEquals(100L, captor.getValue().getInputAmount());
    }

    @Test
    @DisplayName("track 特定服務代碼未配置時應自動 Fallback 至全域平台設定 (DISCORD)")
    void track_ShouldFallbackToGlobalPlatformLimit_whenSpecificServiceMissing() {
        when(botConfigLoader.get("discord-gf", "cost_limit_daily")).thenReturn(null);
        when(botConfigLoader.get("DISCORD", "cost_limit_daily")).thenReturn("5.0");
        when(apiUsageLogDataAccess.findByServiceAndCreatedTimeBetween(eq("discord-gf"), any(), any()))
                .thenReturn(Collections.emptyList());

        boolean result = service.track("discord-gf", "chat", "char", 50L);

        assertTrue(result);
        verify(apiUsageLogDataAccess).save(any(ApiUsageLog.class));
    }

    @Test
    @DisplayName("track 特定服務代碼未配置時應自動 Fallback 至全域平台設定 (LINE)")
    void track_ShouldFallbackToGlobalPlatformLimit_forLineService() {
        when(botConfigLoader.get("line-gf", "cost_limit_daily")).thenReturn(null);
        when(botConfigLoader.get("LINE", "cost_limit_daily")).thenReturn("3.0");
        when(apiUsageLogDataAccess.findByServiceAndCreatedTimeBetween(eq("line-gf"), any(), any()))
                .thenReturn(Collections.emptyList());

        boolean result = service.track("line-gf", "chat", "char", 50L);

        assertTrue(result);
        verify(apiUsageLogDataAccess).save(any(ApiUsageLog.class));
    }

    @Test
    @DisplayName("track 當累積花費超過上限時應拒絕呼叫 (回傳 false) 且不保存記錄")
    void track_ShouldRejectCall_whenLimitExceeded() {
        when(botConfigLoader.get("discord-gf", "cost_limit_daily")).thenReturn("0.05");

        ApiUsageLog pastLog = new ApiUsageLog();
        pastLog.setEstimatedCost(new BigDecimal("0.045"));
        when(apiUsageLogDataAccess.findByServiceAndCreatedTimeBetween(eq("discord-gf"), any(), any()))
                .thenReturn(List.of(pastLog));

        // 100 char chat cost is 0.100, which added to 0.045 exceeds 0.05 limit
        boolean result = service.track("discord-gf", "chat", "char", 100L);

        assertFalse(result);
        verify(apiUsageLogDataAccess, never()).save(any(ApiUsageLog.class));
    }
}
