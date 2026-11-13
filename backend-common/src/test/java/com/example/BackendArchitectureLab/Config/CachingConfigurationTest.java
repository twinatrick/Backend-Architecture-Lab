package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Util.CacheExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class CachingConfigurationTest {

    @Configuration
    static class CacheManagerConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Test
    @DisplayName("Servlet 應用且有 CacheManager 時應註冊 CacheExecutor")
    void servletApplication_RegistersCacheExecutor() {
        new WebApplicationContextRunner()
                .withUserConfiguration(CacheManagerConfig.class, CachingConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(CacheExecutor.class));
    }

    @Test
    @DisplayName("Reactive 應用（如 Gateway）不應載入 CacheExecutor，也不應要求 CacheManager")
    void reactiveApplication_DoesNotRegisterCacheExecutor() {
        new ReactiveWebApplicationContextRunner()
                .withUserConfiguration(CachingConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CacheExecutor.class);
                });
    }
}
