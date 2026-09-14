package com.example.BackendArchitectureLab.TestSupport;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 混沌驗證整合測試基底抽象類別
 * 自動綁定 Toxiproxy 代理通道，並在每個測試執行完畢後重設網路故障狀態
 */
public abstract class BaseChaosIntegrationTest implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    protected ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @DynamicPropertySource
    static void configureChaosSharedProperties(DynamicPropertyRegistry registry) {
        // Kafka 動態配置
        registry.add("spring.kafka.bootstrap-servers", SharedChaosContainers::getKafkaBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");

        // Redis 透過 Toxiproxy 代理
        registry.add("spring.data.redis.host", SharedChaosContainers::getRedisHost);
        registry.add("spring.data.redis.port", SharedChaosContainers::getRedisPort);

        // Redisson 動態配置透過 Toxiproxy 代理
        registry.add("spring.redis.redisson.config", () ->
                "singleServerConfig:\n  address: \"redis://" +
                SharedChaosContainers.getRedisHost() + ":" + SharedChaosContainers.getRedisPort() + "\"\n");

        // 關閉測試環境下的 Nacos 服務註冊與設定發現
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");

        // 調校租約與輪詢窗口為秒級，加快混沌測試執行與判定速度 (依據 Grilling Q9 決策)
        registry.add("compensation.lease.duration-ms", () -> "2000");
        registry.add("compensation.lease.poll-interval-ms", () -> "200");
    }

    @AfterEach
    void tearDownChaosState() {
        // 1. 重設所有 Toxiproxy 故障注入狀態
        SharedChaosContainers.resetAllToxics();

        // 2. 清理 DB 與 Redis
        if (applicationContext != null) {
            try {
                DatabaseCleaner databaseCleaner = applicationContext.getBean(DatabaseCleaner.class);
                databaseCleaner.clean();
            } catch (BeansException ignored) {
            }

            try {
                StringRedisTemplate stringRedisTemplate = applicationContext.getBean(StringRedisTemplate.class);
                var connectionFactory = stringRedisTemplate.getConnectionFactory();
                if (connectionFactory != null) {
                    try (var connection = connectionFactory.getConnection()) {
                        connection.serverCommands().flushDb();
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
}
