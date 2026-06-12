package com.example.BackendArchitectureLab.Aop;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Dto.Vo.ResponseType;
import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Aspect
@Order(2)
@Component
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PermissionCheck {

    private final PermissionCheckFeignClient permissionCheckFeignClient;

    @Around("execution(* com.example.BackendArchitectureLab.Controller..*.*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        RequirePermission requirePermission = resolveRequirePermission(joinPoint);
        if (requirePermission == null) {
            return joinPoint.proceed();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return unauthorizedResponse();
        }

        String email = auth.getName();
        if (email == null || email.isBlank()) {
            return unauthorizedResponse();
        }

        List<String> permissionPath = Arrays.stream(requirePermission.value())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();

        if (permissionPath.size() != 3) {
            return forbiddenResponse();
        }

        boolean matched = permissionCheckFeignClient.validatePermission(
                email,
                permissionPath.get(0),
                permissionPath.get(1),
                permissionPath.get(2));

        if (matched) {
            return joinPoint.proceed();
        }

        return forbiddenResponse();
    }

    private RequirePermission resolveRequirePermission(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        RequirePermission methodAnnotation = method.getAnnotation(RequirePermission.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }

        Class<?> targetClass = joinPoint.getTarget().getClass();
        RequirePermission classAnnotation = AnnotationUtils.findAnnotation(targetClass, RequirePermission.class);
        if (classAnnotation != null) {
            return classAnnotation;
        }

        return AnnotationUtils.findAnnotation(method.getDeclaringClass(), RequirePermission.class);
    }

    private Object unauthorizedResponse() {
        setResponseStatus(HttpStatus.UNAUTHORIZED.value());
        return ResponseType.Fail("AUTH_ERROR", "Unauthorized", 401);
    }

    private Object forbiddenResponse() {
        setResponseStatus(HttpStatus.FORBIDDEN.value());
        return ResponseType.Fail("FORBIDDEN", "Forbidden", 403);
    }

    private void setResponseStatus(int status) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            HttpServletResponse response = servletRequestAttributes.getResponse();
            if (response != null) {
                response.setStatus(status);
            }
        }
    }
}
