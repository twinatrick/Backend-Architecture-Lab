package com.example.BackendArchitectureLab.Config;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class MealStorageConfig {
    @Bean
    public MealStorageClient mealStorageClient(@Value("${minio.url}") String url,
                                               @Value("${minio.access-key}") String accessKey,
                                               @Value("${minio.secret-key}") String secretKey) {
        OkHttpClient http = new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(60)).writeTimeout(Duration.ofSeconds(60))
                .callTimeout(Duration.ofSeconds(90)).build();
        return new MealStorageClient(MinioClient.builder().endpoint(url).credentials(accessKey, secretKey).httpClient(http).build());
    }
}
