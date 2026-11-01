package com.example.BackendArchitectureLab.Config;

import io.minio.MinioClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MinioConfigTest {

    @Test
    @DisplayName("升級後可透過真實 OkHttp JVM 依賴建立 MinIO 用戶端")
    void shouldBuildMinioClientWithJvmDependencies() {
        MinioConfig config = new MinioConfig();
        ReflectionTestUtils.setField(config, "url", "http://localhost:9000");
        ReflectionTestUtils.setField(config, "accessKey", "test-access-key");
        ReflectionTestUtils.setField(config, "secretKey", "test-secret-key");

        MinioClient client = config.minioClient();

        assertNotNull(client);
    }
}
