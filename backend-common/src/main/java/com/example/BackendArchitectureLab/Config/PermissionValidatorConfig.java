package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Aop.DefaultPermissionValidator;
import com.example.BackendArchitectureLab.Aop.LocalPermissionValidator;
import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * 權限驗證器配置：
 * 當服務未註冊自定義 LocalPermissionValidator（例如 IAM 服務本機驗證）時，
 * 自動提供以 Feign 呼叫 IAM 驗證的 DefaultPermissionValidator。
 */
@Configuration
public class PermissionValidatorConfig {

    @Bean
    @ConditionalOnMissingBean(LocalPermissionValidator.class)
    public LocalPermissionValidator defaultPermissionValidator(
            Optional<PermissionCheckFeignClient> permissionCheckFeignClientOptional) {
        return new DefaultPermissionValidator(permissionCheckFeignClientOptional);
    }
}
