package com.example.BackendArchitectureLab.Config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * InternalApiTokenInterceptor - 內網端點共享 token 驗證（H-01，defense-in-depth）。
 * <p>
 * 內網端點（/project/inner/**）依賴 Fencing Token 作業務層防護，但並未驗證呼叫端身份。
 * 本攔截器以共享密鑰（X-Internal-Token）驗證呼叫端；token 未設定時採取 fail-closed
 * （一律 401），避免未授權的雜湊仍能觸發 /project/inner/** 的破壞性端點。
 * 此外啟動時拒絕可被誤用的已知 placeholder 值（如 .env.example 中的範例值）與長度不足的弱密鑰，
 * 確保部署流程不會直接沿用公開的範本密鑰；比對使用 constant-time 比較以防計時攻擊。
 */
@Slf4j
@Component
public class InternalApiTokenInterceptor implements HandlerInterceptor {

    private static final Set<String> FORBIDDEN_PLACEHOLDERS =
            Set.of("your_internal_token", "change_me", "default");

    private static final int MIN_TOKEN_LENGTH = 16;

    @Value("${app.internal.token:}")
    private String internalToken;

    @PostConstruct
    void validateConfiguredToken() {
        if (internalToken != null && FORBIDDEN_PLACEHOLDERS.contains(internalToken.trim().toLowerCase())) {
            throw new IllegalStateException(
                    "app.internal.token 使用了已知的佔位值（" + internalToken
                            + "），請設定真正機密的 internal token 後再啟動");
        }
        if (internalToken == null || internalToken.trim().length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException(
                    "app.internal.token 長度必須至少 " + MIN_TOKEN_LENGTH
                            + " 字元，且為高熵隨機值；請設定真正機密的 internal token 後再啟動");
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (internalToken == null || internalToken.isBlank()
                || internalToken.trim().length() < MIN_TOKEN_LENGTH
                || FORBIDDEN_PLACEHOLDERS.contains(internalToken.trim().toLowerCase())) {
            log.warn("app.internal.token is not properly configured; internal API {} rejected (fail-closed)",
                    request.getRequestURI());
            writeUnauthorized(response);
            return false;
        }

        String provided = request.getHeader("X-Internal-Token");
        if (provided == null || !constantTimeEquals(internalToken, provided)) {
            log.warn("Internal API {} rejected: missing or invalid X-Internal-Token", request.getRequestURI());
            writeUnauthorized(response);
            return false;
        }
        return true;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Unauthorized: missing or invalid X-Internal-Token\"}");
    }
}
