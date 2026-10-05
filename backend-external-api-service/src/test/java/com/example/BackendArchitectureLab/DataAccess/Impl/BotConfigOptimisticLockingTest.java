package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IBotConfigDataAccess;
import com.example.BackendArchitectureLab.Entity.BotConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
@Import(BotConfigDataAccessImpl.class)
@DisplayName("BotConfig JPA 與 @Version 樂觀鎖並發控制測試")
class BotConfigOptimisticLockingTest {

    private final IBotConfigDataAccess botConfigDataAccess;
    private final TestEntityManager testEntityManager;

    @Autowired
    public BotConfigOptimisticLockingTest(
            IBotConfigDataAccess botConfigDataAccess,
            TestEntityManager testEntityManager) {
        this.botConfigDataAccess = botConfigDataAccess;
        this.testEntityManager = testEntityManager;
    }

    @Test
    @DisplayName("新增 BotConfig 時應自動初始化 version 為 0")
    void testVersionInitializationOnCreate() {
        BotConfig config = new BotConfig();
        config.setPlatform("discord");
        config.setConfigKey("command_prefix");
        config.setConfigValue("!");
        config.setDescription("Discord bot command prefix");

        BotConfig saved = botConfigDataAccess.save(config);
        testEntityManager.flush();
        testEntityManager.clear();

        BotConfig retrieved = botConfigDataAccess.findById(saved.getId()).orElseThrow();
        assertNotNull(retrieved.getVersion());
        assertEquals(0L, retrieved.getVersion());
    }

    @Test
    @DisplayName("並發更新 BotConfig 時應偵測版本衝突並拋出 ObjectOptimisticLockingFailureException")
    void testOptimisticLockCollision() {
        BotConfig config = new BotConfig();
        config.setPlatform("line");
        config.setConfigKey("rate_limit");
        config.setConfigValue("100");
        config.setCostLimitDaily(new BigDecimal("50.00"));
        config.setCostAlertAt(new BigDecimal("40.00"));

        BotConfig saved = botConfigDataAccess.save(config);
        testEntityManager.flush();
        testEntityManager.clear();

        // 模擬兩個並發交易同時讀取同一筆 BotConfig
        BotConfig tx1Config = botConfigDataAccess.findById(saved.getId()).orElseThrow();
        testEntityManager.detach(tx1Config);

        BotConfig tx2Config = botConfigDataAccess.findById(saved.getId()).orElseThrow();
        assertEquals(0L, tx1Config.getVersion());
        assertEquals(0L, tx2Config.getVersion());

        // 交易 2 率先更新並 commit/flush -> 資料庫 version 推進至 1
        tx2Config.setConfigValue("200");
        botConfigDataAccess.save(tx2Config);
        testEntityManager.flush();

        // 交易 1 持有過期的 version 0 嘗試更新 -> 觸發樂觀鎖例外
        tx1Config.setConfigValue("300");
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            botConfigDataAccess.save(tx1Config);
            testEntityManager.flush();
        });
    }
}
