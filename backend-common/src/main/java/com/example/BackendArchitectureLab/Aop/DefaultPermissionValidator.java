package com.example.BackendArchitectureLab.Aop;

import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 預設權限驗證器：當服務未提供 LocalPermissionValidatorImpl（例如非 IAM 服務）時使用，
 * 透過 Caffeine 本地快取與 Resilience4j 斷路器保護 Feign 呼叫 IAM 進行權限驗證。
 * 遵循 Fail-Closed 原則，於熔斷或不可用時快速失敗。
 */
@RequiredArgsConstructor
public class DefaultPermissionValidator implements LocalPermissionValidator {

    private static final Logger log = LoggerFactory.getLogger(DefaultPermissionValidator.class);

    private final PermissionCheckFeignClient permissionCheckFeignClient;
    private final CircuitBreaker circuitBreaker;
    private final Cache<String, Boolean> permissionCache;

    @Override
    public boolean validate(String email, String one, String two, String three) {
        if (email == null || email.isBlank()) {
            return false;
        }

        String cacheKey = email + ":" + one + ":" + two + ":" + three;
        Boolean cached = permissionCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Permission cache HIT for key={}: {}", cacheKey, cached);
            return cached;
        }

        log.debug("Permission cache MISS for key={}, calling iam-service via CircuitBreaker", cacheKey);
        try {
            boolean result = circuitBreaker.executeSupplier(() ->
                    permissionCheckFeignClient.validatePermission(email, one, two, three));
            permissionCache.put(cacheKey, result);
            return result;
        } catch (CallNotPermittedException e) {
            log.warn("Permission check CircuitBreaker is OPEN for key={}: {}", cacheKey, e.getMessage());
            throw e;
        }
    }

    public void clearCache() {
        permissionCache.invalidateAll();
    }

    public void invalidate(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String prefix = email + ":";
        permissionCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public Cache<String, Boolean> getPermissionCache() {
        return permissionCache;
    }
}
