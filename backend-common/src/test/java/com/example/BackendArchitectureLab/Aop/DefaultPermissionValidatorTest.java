package com.example.BackendArchitectureLab.Aop;

import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPermissionValidatorTest {

    @Mock
    private PermissionCheckFeignClient permissionCheckFeignClient;

    private CircuitBreaker circuitBreaker;
    private Cache<String, Boolean> cache;
    private DefaultPermissionValidator validator;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .build();
        circuitBreaker = CircuitBreaker.of("testPermissionCheck", config);
        cache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofSeconds(30))
                .build();
        validator = new DefaultPermissionValidator(permissionCheckFeignClient, circuitBreaker, cache);
    }

    @Test
    @DisplayName("空白或空 Email 應直接回傳 false，且不發起 Feign 呼叫")
    void validate_blankOrNullEmail_returnsFalseWithoutFeign() {
        assertThat(validator.validate(null, "Competency", "Skill", "View")).isFalse();
        assertThat(validator.validate("", "Competency", "Skill", "View")).isFalse();
        assertThat(validator.validate("   ", "Competency", "Skill", "View")).isFalse();

        verify(permissionCheckFeignClient, never()).validatePermission(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("首次調用快取未命中 (MISS) 應呼叫 Feign，並將結果存入快取")
    void validate_cacheMiss_callsFeignAndCachesResult() {
        String email = "test@example.com";
        when(permissionCheckFeignClient.validatePermission(email, "Competency", "Skill", "View"))
                .thenReturn(true);

        boolean result1 = validator.validate(email, "Competency", "Skill", "View");
        boolean result2 = validator.validate(email, "Competency", "Skill", "View");

        assertThat(result1).isTrue();
        assertThat(result2).isTrue();
        // 第二次調用為快取命中，Feign 僅調用一次
        verify(permissionCheckFeignClient, times(1))
                .validatePermission(email, "Competency", "Skill", "View");
    }

    @Test
    @DisplayName("當 CircuitBreaker 處於 OPEN 狀態且快取未命中時，應拋出 CallNotPermittedException (Fail-Closed)")
    void validate_circuitBreakerOpen_throwsCallNotPermittedException() {
        String email = "admin@example.com";
        circuitBreaker.transitionToOpenState();

        assertThatThrownBy(() -> validator.validate(email, "Competency", "Project", "Delete"))
                .isInstanceOf(CallNotPermittedException.class);

        verify(permissionCheckFeignClient, never())
                .validatePermission(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("當 CircuitBreaker 處於 OPEN 狀態但本機快取已命中時，應降級由快取直接響應")
    void validate_circuitBreakerOpen_cacheHit_returnsCachedValue() {
        String email = "user@example.com";
        when(permissionCheckFeignClient.validatePermission(email, "Competency", "Skill", "Edit"))
                .thenReturn(true);

        // 第一次正常快取
        boolean result1 = validator.validate(email, "Competency", "Skill", "Edit");
        assertThat(result1).isTrue();

        // 斷路器切入 OPEN
        circuitBreaker.transitionToOpenState();

        // 快取仍有效，應直接回傳 true 而不拋出 CallNotPermittedException
        boolean result2 = validator.validate(email, "Competency", "Skill", "Edit");
        assertThat(result2).isTrue();

        verify(permissionCheckFeignClient, times(1))
                .validatePermission(email, "Competency", "Skill", "Edit");
    }

    @Test
    @DisplayName("clearCache 應清除所有快取項目")
    void clearCache_invalidatesAllEntries() {
        String email = "user@example.com";
        when(permissionCheckFeignClient.validatePermission(email, "Competency", "Skill", "View"))
                .thenReturn(true);

        validator.validate(email, "Competency", "Skill", "View");
        validator.clearCache();
        validator.validate(email, "Competency", "Skill", "View");

        // clear 後再次調用應觸發第二次 Feign
        verify(permissionCheckFeignClient, times(2))
                .validatePermission(email, "Competency", "Skill", "View");
    }

    @Test
    @DisplayName("invalidate 指定使用者應僅清除該使用者的快取項目")
    void invalidate_specificEmail_onlyRemovesTargetUser() {
        String userA = "usera@example.com";
        String userB = "userb@example.com";
        when(permissionCheckFeignClient.validatePermission(userA, "Competency", "Skill", "View"))
                .thenReturn(true);
        when(permissionCheckFeignClient.validatePermission(userB, "Competency", "Skill", "View"))
                .thenReturn(true);

        validator.validate(userA, "Competency", "Skill", "View");
        validator.validate(userB, "Competency", "Skill", "View");

        validator.invalidate(userA);

        validator.validate(userA, "Competency", "Skill", "View"); // miss
        validator.validate(userB, "Competency", "Skill", "View"); // hit

        verify(permissionCheckFeignClient, times(2))
                .validatePermission(userA, "Competency", "Skill", "View");
        verify(permissionCheckFeignClient, times(1))
                .validatePermission(userB, "Competency", "Skill", "View");
    }
}
