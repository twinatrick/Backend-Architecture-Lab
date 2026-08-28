package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Aop.DefaultPermissionValidator;
import com.example.BackendArchitectureLab.Aop.LocalPermissionValidator;
import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 權限驗證器自動配置：當容器中不存在 LocalPermissionValidator（非 IAM 服務）時，
 * 自動配置基於 Feign 客戶端的 DefaultPermissionValidator。
 */
@Configuration
public class PermissionValidatorConfig {

    @Bean
    @ConditionalOnMissingBean(LocalPermissionValidator.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public LocalPermissionValidator defaultPermissionValidator(PermissionCheckFeignClient permissionCheckFeignClient) {
        return new DefaultPermissionValidator(permissionCheckFeignClient);
    }
}
