package com.example.BackendArchitectureLab;

import com.example.BackendArchitectureLab.Util.CacheExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Gateway 啟動回歸測試：使用正式的 {@link GatewayApplication} 元件掃描與 application.yml，
 * 不額外提供 CacheManager，驗證 Gateway 不依賴快取基礎設施仍可啟動。
 * 僅關閉 Nacos 註冊／探索這類外部基礎設施連線，不注入任何正式部署不存在的 bean。
 */
@SpringBootTest(
        classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "jwt.secret.use=gateway-startup-test-secret-0123456789abcdef0123456789abcdef",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.discovery.enabled=false"
        })
class GatewayStartupWithoutCacheManagerTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Gateway 在沒有 CacheManager 的情境下應可啟動，且不應註冊 CacheExecutor")
    void contextLoads_WithoutCacheManager() {
        assertEquals(0, context.getBeanNamesForType(CacheExecutor.class).length);
    }
}
