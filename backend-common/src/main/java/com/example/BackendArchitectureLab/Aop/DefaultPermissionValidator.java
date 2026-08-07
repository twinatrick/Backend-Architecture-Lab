package com.example.BackendArchitectureLab.Aop;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 預設權限驗證器：當服務未提供 LocalPermissionValidator 實作時使用，
 * 沿用父類以 Feign 呼叫 IAM 的預設行為。
 */
@Component
@ConditionalOnMissingBean(LocalPermissionValidator.class)
public class DefaultPermissionValidator extends LocalPermissionValidator {
}