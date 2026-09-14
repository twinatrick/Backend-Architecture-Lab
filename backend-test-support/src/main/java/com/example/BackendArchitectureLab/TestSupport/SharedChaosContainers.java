package com.example.BackendArchitectureLab.TestSupport;

import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;

/**
 * 確定性混沌整合測試專用之 Testcontainers + Toxiproxy 容器管理器
 * 提供網路延遲、分區切斷 (Connection Cut)、抖動等可受控故障注入能力
 */
public final class SharedChaosContainers {

    private static final Logger log = LoggerFactory.getLogger(SharedChaosContainers.class);

    private static final Network NETWORK;
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER;
    private static final KafkaContainer KAFKA_CONTAINER;
    private static final GenericContainer<?> REDIS_CONTAINER;
    private static final ToxiproxyContainer TOXIPROXY_CONTAINER;

    private static final ContainerProxy POSTGRES_PROXY;
    private static final ContainerProxy REDIS_PROXY;

    static {
        NETWORK = Network.newNetwork();

        POSTGRESQL_CONTAINER = new PostgreSQLContainer<>("postgres:16-alpine")
                .withNetwork(NETWORK)
                .withNetworkAliases("chaos-postgres")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("init-dbs.sql"),
                        "/docker-entrypoint-initdb.d/init-dbs.sql"
                );

        KAFKA_CONTAINER = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                .withNetwork(NETWORK);

        REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                .withNetwork(NETWORK)
                .withNetworkAliases("chaos-redis")
                .withExposedPorts(6379);

        TOXIPROXY_CONTAINER = new ToxiproxyContainer(DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.5.0"))
                .withNetwork(NETWORK);

        POSTGRESQL_CONTAINER.start();
        KAFKA_CONTAINER.start();
        REDIS_CONTAINER.start();
        TOXIPROXY_CONTAINER.start();

        POSTGRES_PROXY = TOXIPROXY_CONTAINER.getProxy(POSTGRESQL_CONTAINER, 5432);
        REDIS_PROXY = TOXIPROXY_CONTAINER.getProxy(REDIS_CONTAINER, 6379);

        log.info("SharedChaosContainers 初始化完成: PostgresProxy={}:{}, RedisProxy={}:{}",
                POSTGRES_PROXY.getContainerIpAddress(), POSTGRES_PROXY.getProxyPort(),
                REDIS_PROXY.getContainerIpAddress(), REDIS_PROXY.getProxyPort());
    }

    private SharedChaosContainers() {
    }

    public static ContainerProxy getPostgresProxy() {
        return POSTGRES_PROXY;
    }

    public static ContainerProxy getRedisProxy() {
        return REDIS_PROXY;
    }

    public static PostgreSQLContainer<?> getPostgresqlContainer() {
        return POSTGRESQL_CONTAINER;
    }

    public static GenericContainer<?> getRedisContainer() {
        return REDIS_CONTAINER;
    }

    public static KafkaContainer getKafkaContainer() {
        return KAFKA_CONTAINER;
    }

    public static String getPostgresUrlForDatabase(String databaseName) {
        return String.format(
                "jdbc:postgresql://%s:%d/%s",
                POSTGRES_PROXY.getContainerIpAddress(),
                POSTGRES_PROXY.getProxyPort(),
                databaseName
        );
    }

    public static String getDirectPostgresUrlForDatabase(String databaseName) {
        return String.format(
                "jdbc:postgresql://%s:%d/%s",
                POSTGRESQL_CONTAINER.getHost(),
                POSTGRESQL_CONTAINER.getMappedPort(5432),
                databaseName
        );
    }

    public static String getPostgresUsername() {
        return POSTGRESQL_CONTAINER.getUsername();
    }

    public static String getPostgresPassword() {
        return POSTGRESQL_CONTAINER.getPassword();
    }

    public static String getKafkaBootstrapServers() {
        return KAFKA_CONTAINER.getBootstrapServers();
    }

    public static String getRedisHost() {
        return REDIS_PROXY.getContainerIpAddress();
    }

    public static Integer getRedisPort() {
        return REDIS_PROXY.getProxyPort();
    }

    public static void cutPostgresNetwork() {
        log.warn("混沌注入: 切斷 PostgreSQL 網路通道");
        POSTGRES_PROXY.setConnectionCut(true);
    }

    public static void restorePostgresNetwork() {
        log.info("混沌恢復: 恢復 PostgreSQL 網路通道");
        POSTGRES_PROXY.setConnectionCut(false);
    }

    public static void injectPostgresLatency(String toxicName, long latencyMs) {
        try {
            log.warn("混沌注入: 設定 PostgreSQL 下行延遲 {}ms", latencyMs);
            POSTGRES_PROXY.toxics().latency(toxicName, ToxicDirection.DOWNSTREAM, latencyMs);
        } catch (IOException e) {
            throw new RuntimeException("無法注入 PostgreSQL 延遲", e);
        }
    }

    public static void cutRedisNetwork() {
        log.warn("混沌注入: 切斷 Redis 網路通道");
        REDIS_PROXY.setConnectionCut(true);
    }

    public static void restoreRedisNetwork() {
        log.info("混沌恢復: 恢復 Redis 網路通道");
        REDIS_PROXY.setConnectionCut(false);
    }

    public static void resetAllToxics() {
        log.info("混沌清理: 重設所有 Toxiproxy 故障注入狀態");
        try {
            POSTGRES_PROXY.setConnectionCut(false);
            for (Toxic toxic : POSTGRES_PROXY.toxics().getAll()) {
                toxic.remove();
            }
        } catch (Exception e) {
            log.warn("重設 PostgresProxy 故障異常: {}", e.getMessage());
        }

        try {
            REDIS_PROXY.setConnectionCut(false);
            for (Toxic toxic : REDIS_PROXY.toxics().getAll()) {
                toxic.remove();
            }
        } catch (Exception e) {
            log.warn("重設 RedisProxy 故障異常: {}", e.getMessage());
        }
    }
}
