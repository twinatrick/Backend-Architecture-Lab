package com.example.BackendArchitectureLab.Util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * IAM 服務本地 L1 Caffeine 快取管理元件。
 * 不依賴任何 Service 或 DataAccess，供驗證器、UserDetailsService 與各業務 Service 單向依賴。
 */
@Component
public class IamLocalCacheManager {

    private final Cache<String, Boolean> localPermissionCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(5000)
            .build();

    private final Cache<String, Set<String>> userPermsCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(2000)
            .build();

    private final Cache<String, UserDetails> userDetailsCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(2000)
            .build();

    public Boolean getPermissionDecision(String cacheKey) {
        return localPermissionCache.getIfPresent(cacheKey);
    }

    public void putPermissionDecision(String cacheKey, boolean decision) {
        localPermissionCache.put(cacheKey, decision);
    }

    public Set<String> getUserPerms(String email) {
        return userPermsCache.getIfPresent(email);
    }

    public void putUserPerms(String email, Set<String> perms) {
        userPermsCache.put(email, perms);
    }

    public UserDetails getUserDetails(String email) {
        return userDetailsCache.getIfPresent(email);
    }

    public void putUserDetails(String email, UserDetails userDetails) {
        userDetailsCache.put(email, userDetails);
    }

    public void evictUser(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userPermsCache.invalidate(email);
        userDetailsCache.invalidate(email);
        String prefix = email + ":";
        localPermissionCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void evictAllPermissions() {
        userPermsCache.invalidateAll();
        localPermissionCache.invalidateAll();
    }

    public void evictAll() {
        userPermsCache.invalidateAll();
        localPermissionCache.invalidateAll();
        userDetailsCache.invalidateAll();
    }
}
