package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityUtil {

    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

    public UUID requireCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Current user not found - no authentication");
        }
        String email = auth.getName();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Current user not found - no email in authentication");
        }
        UserVo userVo = userServiceFeignClient.getUserByEmail(email);
        if (userVo == null || userVo.getId() == null) {
            throw new IllegalStateException("Current user not found - user lookup failed");
        }
        return UUID.fromString(userVo.getId());
    }
}
