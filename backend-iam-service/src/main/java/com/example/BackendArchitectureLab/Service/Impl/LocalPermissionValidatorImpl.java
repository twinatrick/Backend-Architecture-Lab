package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Aop.LocalPermissionValidator;
import com.example.BackendArchitectureLab.Service.IFunctionQueryService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.Util.IamLocalCacheManager;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.UserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IAM 本機權限驗證器：改以本機資料驗證，
 * 透過本地 Caffeine 快取與使用者級批次授權集合，
 * 避免 IAM 重複執行三層 SQL 查詢與 Feign 驚群阻塞。
 */
@Component
@RequiredArgsConstructor
public class LocalPermissionValidatorImpl implements LocalPermissionValidator {

    private final IFunctionQueryService functionQueryService;
    private final IUserService userService;
    private final IamLocalCacheManager iamLocalCacheManager;

    @Override
    public boolean validate(String email, String one, String two, String three) {
        if (email == null || email.isBlank()) {
            return false;
        }

        String cacheKey = email + ":" + one + ":" + two + ":" + three;
        Boolean cached = iamLocalCacheManager.getPermissionDecision(cacheKey);
        if (cached != null) {
            return cached;
        }

        Set<String> cachedUserPerms = iamLocalCacheManager.getUserPerms(email);
        if (cachedUserPerms != null) {
            boolean hasPerm = cachedUserPerms.contains("*")
                    || cachedUserPerms.contains(one + ":" + two + ":" + three);
            iamLocalCacheManager.putPermissionDecision(cacheKey, hasPerm);
            return hasPerm;
        }

        UserVo user = userService.getUserWithFullPermissionsByEmail(email);
        if (user == null) {
            iamLocalCacheManager.putPermissionDecision(cacheKey, false);
            return false;
        }

        // 管理員全域放行
        if (isAdmin(user)) {
            iamLocalCacheManager.putUserPerms(email, Set.of("*"));
            iamLocalCacheManager.putPermissionDecision(cacheKey, true);
            return true;
        }

        FunctionVo func3 = functionQueryService.getFunctionByPath(one, two, three);
        if (func3 == null) {
            iamLocalCacheManager.putPermissionDecision(cacheKey, false);
            return false;
        }

        String requiredFunctionId = func3.getId();

        boolean match = user.getPermissions() != null && user.getPermissions().stream()
                .map(FunctionVo::getId)
                .filter(Objects::nonNull)
                .anyMatch(id -> id.equals(requiredFunctionId));

        iamLocalCacheManager.putPermissionDecision(cacheKey, match);
        return match;
    }

    @Override
    public Set<String> getUserPermissions(String email) {
        if (email == null || email.isBlank()) {
            return Collections.emptySet();
        }

        Set<String> cached = iamLocalCacheManager.getUserPerms(email);
        if (cached != null) {
            return cached;
        }

        synchronized (email.intern()) {
            Set<String> secondCheck = iamLocalCacheManager.getUserPerms(email);
            if (secondCheck != null) {
                return secondCheck;
            }

            UserVo user = userService.getUserWithFullPermissionsByEmail(email);
            if (user == null) {
                return Collections.emptySet();
            }

            if (isAdmin(user)) {
                Set<String> adminPerms = Set.of("*");
                iamLocalCacheManager.putUserPerms(email, adminPerms);
                return adminPerms;
            }

            if (user.getPermissions() == null || user.getPermissions().isEmpty()) {
                Set<String> emptyPerms = Collections.emptySet();
                iamLocalCacheManager.putUserPerms(email, emptyPerms);
                return emptyPerms;
            }

            List<FunctionVo> allFuncs = functionQueryService.getFunction();
            Map<String, FunctionVo> funcMap = allFuncs.stream()
                    .filter(f -> f.getId() != null)
                    .collect(Collectors.toMap(FunctionVo::getId, f -> f, (first, second) -> first));

            Set<String> result = new HashSet<>();
            for (FunctionVo f3 : user.getPermissions()) {
                if (f3.getParent() != null && funcMap.containsKey(f3.getParent())) {
                    FunctionVo f2 = funcMap.get(f3.getParent());
                    if (f2.getParent() != null && funcMap.containsKey(f2.getParent())) {
                        FunctionVo f1 = funcMap.get(f2.getParent());
                        result.add(f1.getName() + ":" + f2.getName() + ":" + f3.getName());
                    }
                }
            }

            iamLocalCacheManager.putUserPerms(email, result);
            return result;
        }
    }

    private boolean isAdmin(UserVo user) {
        if (user.getRoleNames() == null) {
            return false;
        }
        return user.getRoleNames().stream().anyMatch(r ->
                "ADMIN".equalsIgnoreCase(r)
                || "ROLE_ADMIN".equalsIgnoreCase(r)
                || "系統管理員".equals(r)
        );
    }
}