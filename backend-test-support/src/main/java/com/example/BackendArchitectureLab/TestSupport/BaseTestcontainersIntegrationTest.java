package com.example.BackendArchitectureLab.TestSupport;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Testcontainers 整合測試基底抽象類別
 */
public abstract class BaseTestcontainersIntegrationTest {

    @Autowired(required = false)
    protected DatabaseCleaner databaseCleaner;

    @Autowired(required = false)
    protected StringRedisTemplate stringRedisTemplate;

    @DynamicPropertySource
    static void configureSharedProperties(DynamicPropertyRegistry registry) {
        // Kafka 動態配置
        registry.add("spring.kafka.bootstrap-servers", SharedContainers::getKafkaBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");

        // Redis 動態配置
        registry.add("spring.data.redis.host", SharedContainers::getRedisHost);
        registry.add("spring.data.redis.port", SharedContainers::getRedisPort);

        // Redisson 動態配置
        registry.add("spring.redis.redisson.config", () ->
                "singleServerConfig:\n  address: \"redis://" +
                SharedContainers.getRedisHost() + ":" + SharedContainers.getRedisPort() + "\"\n");

        // 關閉測試環境下的 Nacos 服務註冊與設定發現
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
    }

    @AfterEach
    void tearDownSharedState() {
        if (databaseCleaner != null) {
            databaseCleaner.clean();
        }
        if (stringRedisTemplate != null && stringRedisTemplate.getConnectionFactory() != null) {
            try {
                stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
            } catch (Exception ignored) {
            }
        }
    }
}
