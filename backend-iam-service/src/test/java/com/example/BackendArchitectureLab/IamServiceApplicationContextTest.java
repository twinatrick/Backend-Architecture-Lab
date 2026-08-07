package com.example.BackendArchitectureLab;

import com.example.BackendArchitectureLab.Aop.DefaultPermissionValidator;
import com.example.BackendArchitectureLab.Aop.LocalPermissionValidator;
import com.example.BackendArchitectureLab.Service.impl.LocalPermissionValidatorImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * IAM 完整 Spring context 載入驗證：
 * 確保移除 @EnableFeignClients 與 openfeign 依賴後服務仍能正常啟動，
 * 且權限驗證僅註冊單一本機 LocalPermissionValidator bean（無 Default 重複）。
 */
@SpringBootTest
@ActiveProfiles("test")
class IamServiceApplicationContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private RedissonClient redissonClient;

    @Test
    @DisplayName("Application context should load successfully")
    void contextLoads() {
        assertNotNull(applicationContext);
    }

    @Test
    @DisplayName("Only local PermissionValidator should be registered in IAM")
    void onlyLocalPermissionValidatorExists() {
        assertEquals(1, applicationContext.getBeansOfType(LocalPermissionValidator.class).size(),
                "IAM should have exactly one LocalPermissionValidator bean");
        LocalPermissionValidator validator = applicationContext.getBean(LocalPermissionValidator.class);
        assertInstanceOf(LocalPermissionValidatorImpl.class, validator,
                "IAM should use the local implementation instead of default Feign delegate");
        assertEquals(0, applicationContext.getBeansOfType(DefaultPermissionValidator.class).size(),
                "DefaultPermissionValidator must not be registered when a local implementation exists");
    }
}