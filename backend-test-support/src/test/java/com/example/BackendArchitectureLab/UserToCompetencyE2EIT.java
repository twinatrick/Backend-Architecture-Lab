package com.example.BackendArchitectureLab;

import com.example.BackendArchitectureLab.Security.JwtAuthenticationToken;
import com.example.BackendArchitectureLab.TestSupport.BaseTestcontainersIntegrationTest;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest(classes = TestSupportApp.class)
public class UserToCompetencyE2EIT extends BaseTestcontainersIntegrationTest {

    private final String testSecret = "secret-key-that-is-at-least-32-bytes-long-for-hmac-sha256-security";
    private final String testIssuer = "iam-service";
    private final String testAudience = "backend-services";

    @Test
    @DisplayName("跨微服務認證合約：IAM 簽發 JWT Token 經由通用安全性模組解析與宣告驗證")
    void testCrossServiceJwtTokenIssuanceAndVerification() throws Exception {
        JwtAuthenticationToken jwtUtil = new JwtAuthenticationToken(testSecret, testIssuer, testAudience, 60);

        String userEmail = "developer-alice@example.com";

        // 1. IAM 服務產生 JWT Token
        String token = jwtUtil.generateJWT(userEmail);
        assertNotNull(token, "產生的 JWT Token 不應為空");

        // 2. 下游微服務（如 Competency Service）驗證 Token 並提取宣告
        JwtClaims parsedClaims = jwtUtil.verifyJWT(token);
        assertNotNull(parsedClaims, "解析出的宣告不得為空");

        assertEquals(userEmail, parsedClaims.getSubject());
        assertEquals(userEmail, parsedClaims.getClaimValue("email"));
        assertEquals(testIssuer, parsedClaims.getIssuer());
    }

    @Test
    @DisplayName("JWT 偽造防護：使用未授權密鑰簽發之 Token 跨服務驗證時應拒絕並拋出 InvalidJwtException")
    void testTamperedOrInvalidSecretJwtVerificationFails() throws Exception {
        JwtAuthenticationToken issuerUtil = new JwtAuthenticationToken("wrong-fake-secret-key-that-is-32-bytes-long!", testIssuer, testAudience, 60);
        JwtAuthenticationToken verifierUtil = new JwtAuthenticationToken(testSecret, testIssuer, testAudience, 60);

        String forgedToken = issuerUtil.generateJWT("hacker@malicious.com");

        assertThrows(InvalidJwtException.class, () -> {
            verifierUtil.verifyJWT(forgedToken);
        }, "使用錯誤密鑰簽發的 Token 應遭驗證器拒絕");
    }
}
