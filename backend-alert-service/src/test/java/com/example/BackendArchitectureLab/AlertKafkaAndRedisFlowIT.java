package com.example.BackendArchitectureLab;

import com.example.BackendArchitectureLab.Entity.AlertCheckLimit;
import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import com.example.BackendArchitectureLab.Repository.AlertCheckLimitRepository;
import com.example.BackendArchitectureLab.TestSupport.BaseTestcontainersIntegrationTest;
import com.example.BackendArchitectureLab.TestSupport.SharedContainers;
import com.example.BackendArchitectureLab.Vo.CacheStatsEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ActiveProfiles("test")
@SpringBootTest(classes = AlertServiceApplication.class)
public class AlertKafkaAndRedisFlowIT extends BaseTestcontainersIntegrationTest {

    @Autowired
    private KafkaTemplate<String, CacheStatsEvent> cacheStatsKafkaTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AlertCheckLimitRepository alertCheckLimitRepository;

    @MockBean
    private PermissionCheckFeignClient permissionCheckFeignClient;

    @DynamicPropertySource
    static void configureAlertProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> SharedContainers.getPostgresUrlForDatabase("alert_service"));
        registry.add("spring.datasource.username", SharedContainers::getPostgresUsername);
        registry.add("spring.datasource.password", SharedContainers::getPostgresPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        String testGroupId = "alert-test-group-" + UUID.randomUUID();
        registry.add("spring.kafka.consumer.group-id", () -> testGroupId);
        registry.add("kafka.consumer.group-id", () -> testGroupId);
    }

    @BeforeEach
    void setUp() {
        alertCheckLimitRepository.deleteAll();
    }

    @Test
    @DisplayName("Kafka 快取統計指標串流至 Redis Hash 聚合驗證")
    void testCacheStatsKafkaToRedisStream() {
        String cacheName = "alert-test-cache-" + UUID.randomUUID();

        cacheStatsKafkaTemplate.send("cache-stats", new CacheStatsEvent(cacheName, "hit"));
        cacheStatsKafkaTemplate.send("cache-stats", new CacheStatsEvent(cacheName, "hit"));
        cacheStatsKafkaTemplate.send("cache-stats", new CacheStatsEvent(cacheName, "miss"));

        String redisKey = "cache:stats:" + cacheName;

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Object hitCount = stringRedisTemplate.opsForHash().get(redisKey, "hit");
                    Object missCount = stringRedisTemplate.opsForHash().get(redisKey, "miss");
                    assertEquals("2", hitCount, "Redis 中的 hit 計數應為 2");
                    assertEquals("1", missCount, "Redis 中的 miss 計數應為 1");
                });
    }

    @Test
    @DisplayName("PostgreSQL 告警閾值持久化與查詢驗證")
    void testAlertCheckLimitDatabasePersistence() {
        AlertCheckLimit limit = new AlertCheckLimit();
        limit.setTableName("water_sensor_data");
        limit.setColumnName("turbidity");
        limit.setLimitValue(45.5);
        alertCheckLimitRepository.save(limit);

        List<AlertCheckLimit> results = alertCheckLimitRepository.findAlertCheckLimitByTableNameAndColumnName(
                "water_sensor_data",
                "turbidity"
        );

        assertFalse(results.isEmpty());
        assertEquals(45.5, results.getFirst().getLimitValue());
    }
}
