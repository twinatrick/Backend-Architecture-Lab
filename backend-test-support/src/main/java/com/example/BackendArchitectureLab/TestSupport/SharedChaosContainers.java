package com.example.BackendArchitectureLab.TestSupport;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;

/**
 * 確定性混沌整合測試相容門面
 * 全數委託至 {@link SharedContainers} 統一容器池，防止重複啟動容器耗盡資源
 */
public final class SharedChaosContainers {

    private SharedChaosContainers() {
    }

    public static ContainerProxy getPostgresProxy() {
        return SharedContainers.getPostgresProxy();
    }

    public static ContainerProxy getRedisProxy() {
        return SharedContainers.getRedisProxy();
    }

    public static PostgreSQLContainer<?> getPostgresqlContainer() {
        return SharedContainers.getPostgresqlContainer();
    }

    public static GenericContainer<?> getRedisContainer() {
        return SharedContainers.getRedisContainer();
    }

    public static KafkaContainer getKafkaContainer() {
        return SharedContainers.getKafkaContainer();
    }

    public static String getPostgresUrlForDatabase(String databaseName) {
        return SharedContainers.getPostgresUrlForDatabase(databaseName);
    }

    public static String getDirectPostgresUrlForDatabase(String databaseName) {
        return SharedContainers.getDirectPostgresUrlForDatabase(databaseName);
    }

    public static String getPostgresUsername() {
        return SharedContainers.getPostgresUsername();
    }

    public static String getPostgresPassword() {
        return SharedContainers.getPostgresPassword();
    }

    public static String getKafkaBootstrapServers() {
        return SharedContainers.getKafkaBootstrapServers();
    }

    public static String getRedisHost() {
        return SharedContainers.getRedisHost();
    }

    public static Integer getRedisPort() {
        return SharedContainers.getRedisPort();
    }

    public static void cutPostgresNetwork() {
        SharedContainers.cutPostgresNetwork();
    }

    public static void restorePostgresNetwork() {
        SharedContainers.restorePostgresNetwork();
    }

    public static void injectPostgresLatency(String toxicName, long latencyMs) {
        SharedContainers.injectPostgresLatency(toxicName, latencyMs);
    }

    public static void cutRedisNetwork() {
        SharedContainers.cutRedisNetwork();
    }

    public static void restoreRedisNetwork() {
        SharedContainers.restoreRedisNetwork();
    }

    public static void resetAllToxics() {
        SharedContainers.resetAllToxics();
    }
}
