package com.example.BackendArchitectureLab.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IamLocalCacheManagerTest {

    private IamLocalCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new IamLocalCacheManager();
    }

    @Test
    @DisplayName("evictUser 應清除該使用者對應的權限、角色集合與 UserDetails 本地快取")
    void evictUser_ShouldClearUserSpecificEntries() {
        cacheManager.putPermissionDecision("alice@example.com:Iam:User:View", true);
        cacheManager.putPermissionDecision("bob@example.com:Iam:User:View", true);
        cacheManager.putUserPerms("alice@example.com", Set.of("f1"));
        UserDetails userDetails = new User("alice@example.com", "pwd", Collections.emptyList());
        cacheManager.putUserDetails("alice@example.com", userDetails);

        cacheManager.evictUser("alice@example.com");

        assertNull(cacheManager.getPermissionDecision("alice@example.com:Iam:User:View"));
        assertTrue(cacheManager.getPermissionDecision("bob@example.com:Iam:User:View"));
        assertNull(cacheManager.getUserPerms("alice@example.com"));
        assertNull(cacheManager.getUserDetails("alice@example.com"));
    }

    @Test
    @DisplayName("evictAll 與 evictAllPermissions 應清除對應 L1 本地快取")
    void evictAll_ShouldInvalidateAllCaches() {
        cacheManager.putPermissionDecision("alice@example.com:Iam:User:View", true);
        cacheManager.putUserPerms("alice@example.com", Set.of("f1"));
        UserDetails userDetails = new User("alice@example.com", "pwd", Collections.emptyList());
        cacheManager.putUserDetails("alice@example.com", userDetails);

        cacheManager.evictAllPermissions();
        assertNull(cacheManager.getPermissionDecision("alice@example.com:Iam:User:View"));
        assertNull(cacheManager.getUserPerms("alice@example.com"));
        assertNotNull(cacheManager.getUserDetails("alice@example.com"));

        cacheManager.evictAll();
        assertNull(cacheManager.getUserDetails("alice@example.com"));
    }

    @Test
    @DisplayName("evictUser 傳入空白或 null 時應安全返回")
    void evictUser_BlankOrNull_ShouldNoOp() {
        cacheManager.putPermissionDecision("alice@example.com:Iam:User:View", true);
        cacheManager.evictUser(null);
        cacheManager.evictUser("   ");
        assertNotNull(cacheManager.getPermissionDecision("alice@example.com:Iam:User:View"));
        assertEquals(Boolean.TRUE, cacheManager.getPermissionDecision("alice@example.com:Iam:User:View"));
    }
}
