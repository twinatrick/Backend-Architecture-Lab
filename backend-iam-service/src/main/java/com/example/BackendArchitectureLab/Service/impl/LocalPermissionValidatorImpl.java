package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Aop.LocalPermissionValidator;
import com.example.BackendArchitectureLab.Service.IFunctionService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.UserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * IAM 本機權限驗證器：覆寫預設 Feign 呼叫，改以本機資料驗證，
 * 避免 IAM 自我 Feign 呼叫自身的權限驗證端點。
 */
@Component
public class LocalPermissionValidatorImpl extends LocalPermissionValidator {

    @Autowired
    private IFunctionService functionService;
    @Autowired
    private IUserService userService;

    @Override
    public boolean validate(String email, String one, String two, String three) {
        FunctionVo func1 = functionService.getFunctionByName(one);
        if (func1 == null) {
            return false;
        }
        FunctionVo func2 = functionService.getFunctionByNameAndParent(two, func1.getId());
        if (func2 == null) {
            return false;
        }
        FunctionVo func3 = functionService.getFunctionByNameAndParent(three, func2.getId());
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