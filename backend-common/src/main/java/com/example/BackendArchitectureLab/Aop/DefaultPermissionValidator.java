package com.example.BackendArchitectureLab.Aop;

import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;

import java.util.Optional;

/**
 * 預設權限驗證器：當服務未提供 LocalPermissionValidator 實作時使用，
 * 沿用父類以 Feign 呼叫 IAM 的預設行為。由 PermissionValidatorConfig 條件化註冊。
 */
public class DefaultPermissionValidator extends LocalPermissionValidator {

    public DefaultPermissionValidator(
            Optional<PermissionCheckFeignClient> permissionCheckFeignClientOptional) {
        super(permissionCheckFeignClientOptional);
    }
}
