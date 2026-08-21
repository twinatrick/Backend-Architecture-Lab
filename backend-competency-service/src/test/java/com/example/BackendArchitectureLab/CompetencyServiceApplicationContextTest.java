package com.example.BackendArchitectureLab;

import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Service.ICompensationRestoreStateService;
import com.example.BackendArchitectureLab.Service.IProjectCommandService;
import com.example.BackendArchitectureLab.Service.IProjectSkillService;
import com.example.BackendArchitectureLab.Service.IProjectUserBindingService;
import com.example.BackendArchitectureLab.Service.ISkillService;
import com.example.BackendArchitectureLab.Service.IUserProjectService;
import com.example.BackendArchitectureLab.Vo.CacheStatsEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Competency 完整 Spring ApplicationContext 載入與 @Lazy 代理注入驗證：
 * 確保所有依賴 @Lazy private final I...Service self 注入的 Service Bean
 * 在 Spring 容器啟動時正確以 Lazy Proxy 解析，無循環依賴（BeanCurrentlyInCreationException）。
 */
@SpringBootTest(classes = CompetencyApplication.class)
@ActiveProfiles("test")
class CompetencyServiceApplicationContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private KafkaTemplate<String, CacheStatsEvent> cacheStatsKafkaTemplate;

    @MockBean
    private UserServiceFeignClient userServiceFeignClient;

    @MockBean
    private PermissionCheckFeignClient permissionCheckFeignClient;

    @Test
    @DisplayName("Competency Spring ApplicationContext 應正常加載成功")
    void contextLoads() {
        assertNotNull(applicationContext);
    }

    @Test
    @DisplayName("所有使用 @Lazy 自調用代理之 Service 應成功註冊並實例化")
    void lazySelfProxyServicesShouldBeInstantiated() {
        assertNotNull(applicationContext.getBean(ICompensationRestoreStateService.class));
        assertNotNull(applicationContext.getBean(IProjectSkillService.class));
        assertNotNull(applicationContext.getBean(ISkillService.class));
        assertNotNull(applicationContext.getBean(IProjectUserBindingService.class));
        assertNotNull(applicationContext.getBean(IUserProjectService.class));
        assertNotNull(applicationContext.getBean(IProjectCommandService.class));
    }
}
