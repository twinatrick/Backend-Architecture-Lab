package com.example.BackendArchitectureLab.Aop;

import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 預設權限驗證器：當服務未提供 LocalPermissionValidatorImpl（例如非 IAM 服務）時使用，
 * 沿用父類以 Feign 呼叫 IAM 的預設行為。
 */
@Component
@ConditionalOnMissingBean(type = "com.example.BackendArchitectureLab.Service.Impl.LocalPermissionValidatorImpl")
public class DefaultPermissionValidator extends LocalPermissionValidator {

    public DefaultPermissionValidator(
            Optional<PermissionCheckFeignClient> permissionCheckFeignClientOptional) {
        super(permissionCheckFeignClientOptional);
    }
}
