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
import org.springframework.beans.factory.annotation.Value;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 權限檢查切面：動態解析三層權限路徑 {@code {微服務層, 資源層, 動作層}}。
 * <ul>
 *   <li>第一層（微服務）：由 {@code spring.application.name} 去除 {@code -service/-api} 後綴轉 PascalCase。</li>
 *   <li>第二層（資源層）：方法級或類別級 {@code layer()} 覆寫；未覆寫時用 Controller 類名去除「Controller」後綴。</li>
 *   <li>第三層（動作層）：方法級或類別級 {@code value()}。</li>
 * </ul>
 */
@Aspect
@Order(2)
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PermissionCheck {

    private static final Logger log = LoggerFactory.getLogger(PermissionCheck.class);

    private static final String CONTROLLER_SUFFIX = "Controller";

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    @Autowired
    private LocalPermissionValidator localPermissionValidator;

    @Around("execution(* com.example.BackendArchitectureLab.Controller..*.*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        RequirePermission methodAnnotation = resolveMethodAnnotation(joinPoint);
        RequirePermission classAnnotation = resolveClassAnnotation(joinPoint);
        if (methodAnnotation == null && classAnnotation == null) {
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

        String module = toPascalCase(resolveModuleName(applicationName));
        String layer = resolveLayer(joinPoint, methodAnnotation, classAnnotation);
        String action = firstNonBlank(methodAnnotation == null ? null : methodAnnotation.value(),
                classAnnotation == null ? null : classAnnotation.value());

        if (module.isBlank() || layer.isBlank() || action == null || action.isBlank()) {
            log.warn("Invalid permission for {}: module={}, layer={}, action={}", methodName, module, layer, action);
            return forbiddenResponse();
        }

        log.debug("Checking permission: user={}, path={}/{}/{}, method={}", email, module, layer, action, methodName);

        boolean matched;
        try {
            matched = localPermissionValidator.validate(email, module, layer, action);
        } catch (Exception e) {
            Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
            if (cause instanceof FeignException fe) {
                log.error("Permission check Feign call failed for user={}, path={}/{}/{}: status={}, message={}",
                        email, module, layer, action, fe.status(), fe.getMessage());
                setResponseStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                return ResponseType.Fail("FEIGN_ERROR",
                        "Permission service unavailable: " + fe.getMessage(), 503);
            } else {
                log.error("Permission check local call failed for user={}, path={}/{}/{}: message={}",
                        email, module, layer, action, cause.getMessage());
                setResponseStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                return ResponseType.Fail("PERMISSION_ERROR",
                        "Permission service unavailable: " + cause.getMessage(), 503);
            }
        }

        if (matched) {
            return joinPoint.proceed();
        }

        log.warn("Permission denied for user={}, path={}/{}/{}", email, module, layer, action);
        return forbiddenResponse();
    }

    private RequirePermission resolveMethodAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod().getAnnotation(RequirePermission.class);
    }

    private RequirePermission resolveClassAnnotation(ProceedingJoinPoint joinPoint) {
        Class<?> targetClass = joinPoint.getTarget().getClass();
        return AnnotationUtils.findAnnotation(targetClass, RequirePermission.class);
    }

    /**
     * 第二層解析：優先方法級 layer()，其次類別級 layer()，最後回退 Controller 類名去除後綴。
     */
    private String resolveLayer(ProceedingJoinPoint joinPoint, RequirePermission methodAnnotation,
                                RequirePermission classAnnotation) {
        String layer = firstNonBlank(methodAnnotation == null ? null : methodAnnotation.layer(),
                classAnnotation == null ? null : classAnnotation.layer());
        if (layer != null && !layer.isBlank()) {
            return layer;
        }
        String simpleName = joinPoint.getTarget().getClass().getSimpleName();
        if (simpleName.endsWith(CONTROLLER_SUFFIX)) {
            return simpleName.substring(0, simpleName.length() - CONTROLLER_SUFFIX.length());
        }
        return simpleName;
    }

    private String resolveModuleName(String applicationNameValue) {
        if (applicationNameValue == null || applicationNameValue.isBlank()) {
            return "Unknown";
        }
        String name = applicationNameValue;
        if (name.endsWith("-api")) {
            name = name.substring(0, name.length() - "-api".length());
        }
        if (name.endsWith("-service")) {
            name = name.substring(0, name.length() - "-service".length());
        }
        return name;
    }

    private String toPascalCase(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        String[] parts = raw.split("[-_. ]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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