package com.example.BackendArchitectureLab.TestSupport;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Testcontainers 整合測試基底抽象類別
 */
public abstract class BaseTestcontainersIntegrationTest implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    protected ApplicationContext getApplicationContext() {
        return applicationContext;
    }

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
        if (applicationContext != null) {
            try {
                DatabaseCleaner databaseCleaner = applicationContext.getBean(DatabaseCleaner.class);
                databaseCleaner.clean();
            } catch (BeansException ignored) {
            }

            try {
                StringRedisTemplate stringRedisTemplate = applicationContext.getBean(StringRedisTemplate.class);
                if (stringRedisTemplate.getConnectionFactory() != null) {
                    stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
