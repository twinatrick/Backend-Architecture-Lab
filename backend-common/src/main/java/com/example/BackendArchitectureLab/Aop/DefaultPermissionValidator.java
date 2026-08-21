package com.example.BackendArchitectureLab.Aop;

import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 預設權限驗證器：當服務未提供 LocalPermissionValidatorImpl（例如非 IAM 服務）時使用，
 * 透過 Feign 呼叫 IAM 進行權限驗證。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnMissingBean(type = "com.example.BackendArchitectureLab.Service.Impl.LocalPermissionValidatorImpl")
public class DefaultPermissionValidator implements LocalPermissionValidator {

    private final PermissionCheckFeignClient permissionCheckFeignClient;

    @Override
    public boolean validate(String email, String one, String two, String three) {
        return permissionCheckFeignClient.validatePermission(email, one, two, three);
    }
}
