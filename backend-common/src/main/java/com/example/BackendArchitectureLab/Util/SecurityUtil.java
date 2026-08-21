package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityUtil {

    private final Optional<UserServiceFeignClient> userServiceFeignClient;

    public SecurityUtil(Optional<UserServiceFeignClient> userServiceFeignClientOptional) {
        this.userServiceFeignClient = Objects.requireNonNullElseGet(
                userServiceFeignClientOptional,
                Optional::empty
        );
    }

    public UUID requireCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Current user not found - no authentication");
        }
        String email = auth.getName();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Current user not found - no email in authentication");
        }
        UserServiceFeignClient feignClient = userServiceFeignClient.orElseThrow(() ->
                new IllegalStateException(
                        "Current user not found - UserServiceFeignClient is not available in this service")
        );
        UserVo userVo = feignClient.getUserByEmail(email);
        if (userVo == null || userVo.getId() == null) {
            throw new IllegalStateException("Current user not found - user lookup failed");
        }
        return UUID.fromString(userVo.getId());
    }
}
