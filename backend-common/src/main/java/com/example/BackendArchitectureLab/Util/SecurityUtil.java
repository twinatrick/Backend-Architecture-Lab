package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Security.AuthenticatedUser;
import com.example.BackendArchitectureLab.Security.JwtAuthenticationDetails;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
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

        // 1. 優先從 AuthenticatedUser Principal 取得（非 IAM 微服務就地解析）
        if (auth.getPrincipal() instanceof AuthenticatedUser authUser
                && authUser.getUserId() != null
                && !authUser.getUserId().isBlank()) {
            return UUID.fromString(authUser.getUserId());
        }

        // 2. 從 JwtAuthenticationDetails 取得
        if (auth.getDetails() instanceof JwtAuthenticationDetails jwtDetails
                && jwtDetails.getUserId() != null
                && !jwtDetails.getUserId().isBlank()) {
            return UUID.fromString(jwtDetails.getUserId());
        }

        // 3. 從 Principal 反射取得 getId()（相容 IAM 服務內建的 CustomUserDetails）
        if (auth.getPrincipal() instanceof UserDetails) {
            try {
                Method getUserMethod = auth.getPrincipal().getClass().getMethod("getUser");
                Object userObj = getUserMethod.invoke(auth.getPrincipal());
                if (userObj != null) {
                    Method getIdMethod = userObj.getClass().getMethod("getId");
                    Object idObj = getIdMethod.invoke(userObj);
                    if (idObj instanceof UUID uuid) {
                        return uuid;
                    }
                }
            } catch (Exception ignored) {
                // 忽略反射失敗，fallback 至 Feign 查詢
            }
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

    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return Optional.empty();
        }

        try {
            UUID userId = requireCurrentUserId();
            return Optional.of(userId.toString());
        } catch (Exception ignored) {
            // 若無法解析為 UUID，降級嘗試使用 name (email / username)
        }

        String name = auth.getName();
        if (name != null && !name.isBlank() && !"anonymousUser".equalsIgnoreCase(name)) {
            return Optional.of(name);
        }

        return Optional.empty();
    }
}
