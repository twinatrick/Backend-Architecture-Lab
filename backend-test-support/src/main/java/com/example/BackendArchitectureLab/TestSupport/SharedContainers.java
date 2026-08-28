package com.example.BackendArchitectureLab.TestSupport;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * 共享 Testcontainers 單例容器管理器
 * 在整個測試生命週期中僅啟動一次，大幅減少重複初始化開銷
 */
public final class SharedContainers {

    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER;
    private static final KafkaContainer KAFKA_CONTAINER;
    private static final GenericContainer<?> REDIS_CONTAINER;

    static {
        POSTGRESQL_CONTAINER = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("init-dbs.sql"),
                        "/docker-entrypoint-initdb.d/init-dbs.sql"
                );

        KAFKA_CONTAINER = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

        REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                .withExposedPorts(6379);

        POSTGRESQL_CONTAINER.start();
        KAFKA_CONTAINER.start();
        REDIS_CONTAINER.start();
    }

    private SharedContainers() {
    }

    public static PostgreSQLContainer<?> getPostgresqlContainer() {
        return POSTGRESQL_CONTAINER;
    }

    public static KafkaContainer getKafkaContainer() {
        return KAFKA_CONTAINER;
    }

    public static GenericContainer<?> getRedisContainer() {
        return REDIS_CONTAINER;
    }

    public static String getPostgresUrlForDatabase(String databaseName) {
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
        return REDIS_CONTAINER.getHost();
    }

    public static Integer getRedisPort() {
        return REDIS_CONTAINER.getMappedPort(6379);
    }
}
