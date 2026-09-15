package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Aop.DefaultPermissionValidator;
import com.example.BackendArchitectureLab.Aop.LocalPermissionValidator;
import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 權限驗證器自動配置：當容器中不存在 LocalPermissionValidator（非 IAM 服務）時，
 * 若存在 PermissionCheckFeignClient 則配置基於 Feign 客戶端、Caffeine 本地快取與 Resilience4j 斷路器的 DefaultPermissionValidator；
 * 若均不存在（如獨立容器環境），則回退為拒絕存取（Fail-Closed）驗證器。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PermissionValidatorConfig {

    private static final long DEFAULT_MAX_SIZE = 10_000L;
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    @Bean
    @ConditionalOnMissingBean(LocalPermissionValidator.class)
    @ConditionalOnBean(PermissionCheckFeignClient.class)
    public LocalPermissionValidator defaultPermissionValidator(
            PermissionCheckFeignClient permissionCheckFeignClient,
            ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider) {
        CircuitBreakerRegistry circuitBreakerRegistry = circuitBreakerRegistryProvider.getIfAvailable();
        CircuitBreaker circuitBreaker = circuitBreakerRegistry != null
                ? circuitBreakerRegistry.circuitBreaker("permissionCheck")
                : CircuitBreaker.ofDefaults("permissionCheck");
        Cache<String, Boolean> permissionCache = Caffeine.newBuilder()
                .maximumSize(DEFAULT_MAX_SIZE)
                .expireAfterWrite(DEFAULT_TTL)
                .recordStats()
                .build();
        return new DefaultPermissionValidator(permissionCheckFeignClient, circuitBreaker, permissionCache);
    }

    @Bean
    @ConditionalOnMissingBean({LocalPermissionValidator.class, PermissionCheckFeignClient.class})
    public LocalPermissionValidator fallbackPermissionValidator() {
        return (email, one, two, three) -> false;
    }
}
