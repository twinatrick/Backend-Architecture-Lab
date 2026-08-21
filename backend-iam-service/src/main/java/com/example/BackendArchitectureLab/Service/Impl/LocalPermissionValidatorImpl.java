package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Aop.LocalPermissionValidator;
import com.example.BackendArchitectureLab.Service.IFunctionQueryService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.UserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * IAM 本機權限驗證器：改以本機資料驗證，
 * 避免 IAM 自我 Feign 呼叫自身的權限驗證端點。
 */
@Component
@RequiredArgsConstructor
public class LocalPermissionValidatorImpl implements LocalPermissionValidator {

    private final IFunctionQueryService functionQueryService;
    private final IUserService userService;

    @Override
    public boolean validate(String email, String one, String two, String three) {
        FunctionVo func3 = functionQueryService.getFunctionByPath(one, two, three);
        if (func3 == null) {
            return false;
        }

        String requiredFunctionId = func3.getId();

        UserVo user = userService.getOnlyUserByEmail(email);
        if (user == null) {
            return false;
        }

        return user.getPermissions() != null && user.getPermissions().stream()
                .map(FunctionVo::getId)
                .filter(Objects::nonNull)
                .anyMatch(id -> id.equals(requiredFunctionId));
    }
}