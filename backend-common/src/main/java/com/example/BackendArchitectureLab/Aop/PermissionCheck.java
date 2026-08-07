package com.example.BackendArchitectureLab.Aop;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import feign.FeignException;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PermissionCheck {

    private static final Logger log = LoggerFactory.getLogger(PermissionCheck.class);

    @Autowired
    private LocalPermissionValidator localPermissionValidator;

    @Around("execution(* com.example.BackendArchitectureLab.Controller..*.*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        RequirePermission requirePermission = resolveRequirePermission(joinPoint);
        if (requirePermission == null) {
            log.debug("No permission required for {}", methodName);
            return joinPoint.proceed();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            log.warn("Unauthorized access: no authentication for {}", methodName);
            return unauthorizedResponse();
        }

        String email = auth.getName();
        if (email == null || email.isBlank()) {
            log.warn("Unauthorized access: blank email for {}", methodName);
            return unauthorizedResponse();
        }

        List<String> permissionPath = Arrays.stream(requirePermission.value())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();

        if (permissionPath.size() != 3) {
            log.warn("Invalid permission path size={} for {}: {}", permissionPath.size(), methodName, permissionPath);
            return forbiddenResponse();
        }

        log.debug("Checking permission: user={}, path={}, method={}", email, permissionPath, methodName);

        boolean matched;
        try {
            matched = localPermissionValidator.validate(
                    email,
                    permissionPath.get(0),
                    permissionPath.get(1),
                    permissionPath.get(2));
        } catch (Exception e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
            if (cause instanceof FeignException fe) {
                log.error("Permission check Feign call failed for user={}, path={}: status={}, message={}",
                        email, permissionPath, fe.status(), fe.getMessage());
                setResponseStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                return ResponseType.Fail("FEIGN_ERROR",
                        "Permission service unavailable: " + fe.getMessage(), 503);
            } else {
                log.error("Permission check local call failed for user={}, path={}: message={}",
                        email, permissionPath, cause.getMessage());
                setResponseStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                return ResponseType.Fail("PERMISSION_ERROR",
                        "Permission service unavailable: " + cause.getMessage(), 503);
            }
        }

        if (matched) {
            return joinPoint.proceed();
        }

        log.warn("Permission denied for user={}, path={}", email, permissionPath);
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
