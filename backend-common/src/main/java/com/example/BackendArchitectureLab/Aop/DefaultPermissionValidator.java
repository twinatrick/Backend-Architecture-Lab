package com.example.BackendArchitectureLab.Aop;

import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

/**
 * 預設權限驗證器：當服務未提供 LocalPermissionValidatorImpl（例如非 IAM 服務）時使用，
 * 透過 Caffeine 本地快取（支援使用者級權限集合與單筆權限）與 Resilience4j 斷路器保護 Feign 呼叫 IAM 進行權限驗證。
 * 遵循 Fail-Closed 原則，於熔斷或不可用時快速失敗。
 */
@RequiredArgsConstructor
public class DefaultPermissionValidator implements LocalPermissionValidator {

    private static final Logger log = LoggerFactory.getLogger(DefaultPermissionValidator.class);

    private final PermissionCheckFeignClient permissionCheckFeignClient;
    private final CircuitBreaker circuitBreaker;
    private final Cache<String, Boolean> permissionCache;
    private final Cache<String, Set<String>> userPermissionsCache;

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

        // 優先檢查使用者級權限集合快取
        Set<String> userPerms = userPermissionsCache.getIfPresent(email);
        if (userPerms != null) {
            boolean hasPermission = userPerms.contains("*") || userPerms.contains(one + ":" + two + ":" + three);
            log.debug("User permissions cache HIT for email={}, permission={}: {}", email, cacheKey, hasPermission);
            permissionCache.put(cacheKey, hasPermission);
            return hasPermission;
        }

        // 快取未命中：透過 Single-Flight 鎖合併同一使用者的併發權限預熱請求
        synchronized (email.intern()) {
            Boolean secondCheck = permissionCache.getIfPresent(cacheKey);
            if (secondCheck != null) {
                return secondCheck;
            }
            Set<String> secondUserPerms = userPermissionsCache.getIfPresent(email);
            if (secondUserPerms != null) {
                boolean hasPermission = secondUserPerms.contains("*")
                        || secondUserPerms.contains(one + ":" + two + ":" + three);
                permissionCache.put(cacheKey, hasPermission);
                return hasPermission;
            }

            try {
                Set<String> fetched = circuitBreaker.executeSupplier(() ->
                        permissionCheckFeignClient.getUserPermissions(email));
                if (fetched != null && !fetched.isEmpty()) {
                    userPermissionsCache.put(email, fetched);
                    boolean hasPermission = fetched.contains("*") || fetched.contains(one + ":" + two + ":" + three);
                    log.info("Batch user permissions loaded for email={}, total={}, permission={}: {}",
                            email, fetched.size(), cacheKey, hasPermission);
                    permissionCache.put(cacheKey, hasPermission);
                    return hasPermission;
                }
            } catch (CallNotPermittedException e) {
                log.warn("Permission check CircuitBreaker is OPEN for key={}: {}", cacheKey, e.getMessage());
                throw e;
            } catch (Exception e) {
                log.warn("getUserPermissions fallback to validatePermission for key={}: {}", cacheKey, e.getMessage());
            }
        }

        log.debug("Permission cache MISS for key={}, calling validatePermission via CircuitBreaker", cacheKey);
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

    @Override
    public Set<String> getUserPermissions(String email) {
        if (email == null || email.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> cached = userPermissionsCache.getIfPresent(email);
        if (cached != null) {
            return cached;
        }
        synchronized (email.intern()) {
            Set<String> secondCheck = userPermissionsCache.getIfPresent(email);
            if (secondCheck != null) {
                return secondCheck;
            }
            try {
                Set<String> fetched = circuitBreaker.executeSupplier(() ->
                        permissionCheckFeignClient.getUserPermissions(email));
                if (fetched != null) {
                    userPermissionsCache.put(email, fetched);
                    return fetched;
                }
            } catch (Exception e) {
                log.warn("getUserPermissions error: {}", e.getMessage());
            }
        }
        return Collections.emptySet();
    }

    public void clearCache() {
        permissionCache.invalidateAll();
        userPermissionsCache.invalidateAll();
    }

    public void invalidate(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String prefix = email + ":";
        permissionCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        userPermissionsCache.invalidate(email);
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public Cache<String, Boolean> getPermissionCache() {
        return permissionCache;
    }

    public Cache<String, Set<String>> getUserPermissionsCache() {
        return userPermissionsCache;
    }
}
