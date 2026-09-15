package com.example.BackendArchitectureLab.TestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 混沌驗證整合測試基底抽象類別
 * 繼承 BaseTestcontainersIntegrationTest 共享單一容器池，並將租約窗口調校為秒級
 */
@Timeout(30)
public abstract class BaseChaosIntegrationTest extends BaseTestcontainersIntegrationTest {

    @DynamicPropertySource
    static void configureChaosSharedProperties(DynamicPropertyRegistry registry) {
        // 調校租約與輪詢窗口為秒級，加快混沌測試執行與判定速度 (依據 Grilling Q9 決策)
        registry.add("compensation.lease.duration-ms", () -> "2000");
        registry.add("compensation.lease.poll-interval-ms", () -> "200");
    }

    @AfterEach
    void tearDownChaosState() {
        // 確保在子類別清理階段即重設所有 Toxiproxy 故障狀態，使父類別 DB 清理不受網路切斷影響
        SharedContainers.resetAllToxics();
    }
}
