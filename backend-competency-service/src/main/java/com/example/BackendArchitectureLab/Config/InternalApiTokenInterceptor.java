package com.example.BackendArchitectureLab.Config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * InternalApiTokenInterceptor - 內網端點共享 token 驗證（H-01，defense-in-depth）。
 * <p>
 * 內網端點（/project/inner/**）依賴 Fencing Token 作業務層防護，但並未驗證呼叫端身份。
 * 本攔截器以共享密鑰（X-Internal-Token）驗證呼叫端；token 未設定時採取 fail-closed
 * （一律 401），避免未授權的雜湊仍能觸發 /project/inner/** 的破壞性端點。
 */
@Slf4j
@Component
public class InternalApiTokenInterceptor implements HandlerInterceptor {

    @Value("${app.internal.token:}")
    private String internalToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("app.internal.token is not configured; internal API {} rejected (fail-closed)",
                    request.getRequestURI());
            writeUnauthorized(response);
            return false;
        }

        String provided = request.getHeader("X-Internal-Token");
        if (provided == null || !internalToken.equals(provided)) {
            log.warn("Internal API {} rejected: missing or invalid X-Internal-Token", request.getRequestURI());
            writeUnauthorized(response);
            return false;
        }
        return true;
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Unauthorized: missing or invalid X-Internal-Token\"}");
    }
}
