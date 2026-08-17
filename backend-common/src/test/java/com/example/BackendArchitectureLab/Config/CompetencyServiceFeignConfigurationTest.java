package com.example.BackendArchitectureLab.Config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CompetencyServiceFeignConfigurationTest - 呼叫 competency-service 的 Feign 設定單元測試。
 * 驗證：token 已設定時自動帶 X-Internal-Token header；token 空白（未設定）時不帶。
 */
class CompetencyServiceFeignConfigurationTest {

    private static final String HEADER = "X-Internal-Token";

    @Test
    void configuredToken_shouldAttachInternalTokenHeader() throws Exception {
        CompetencyServiceFeignConfiguration configuration = configWithToken("test-secret");
        RequestInterceptor interceptor = configuration.internalTokenInterceptor();

        RequestTemplate requestTemplate = new RequestTemplate();
        interceptor.apply(requestTemplate);

        Map<String, Collection<String>> headers = requestTemplate.headers();
        assertTrue(headers.containsKey(HEADER));
        assertEquals("test-secret", headers.get(HEADER).iterator().next());
    }

    @Test
    void blankToken_shouldNotAttachHeader() throws Exception {
        CompetencyServiceFeignConfiguration configuration = configWithToken("  ");
        RequestInterceptor interceptor = configuration.internalTokenInterceptor();

        RequestTemplate requestTemplate = new RequestTemplate();
        interceptor.apply(requestTemplate);

        assertFalse(requestTemplate.headers().containsKey(HEADER));
    }

    private CompetencyServiceFeignConfiguration configWithToken(String token) throws Exception {
        CompetencyServiceFeignConfiguration configuration = new CompetencyServiceFeignConfiguration();
        Field field = CompetencyServiceFeignConfiguration.class.getDeclaredField("internalToken");
        field.setAccessible(true);
        field.set(configuration, token);
        return configuration;
    }
}