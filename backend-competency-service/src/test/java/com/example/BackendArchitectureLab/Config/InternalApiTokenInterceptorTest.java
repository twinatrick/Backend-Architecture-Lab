package com.example.BackendArchitectureLab.Config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InternalApiTokenInterceptorTest - 內網端點共享 token（X-Internal-Token）驗證攔截器單元測試。
 * 驗證 H-01：competency /project/inner/** 收到的呼叫需帶正確 token，缺/錯一律 401。
 */
class InternalApiTokenInterceptorTest {

    private static final String API_PATH = "/project/inner/skills/restore";

    private final InternalApiTokenInterceptor interceptor = new InternalApiTokenInterceptor();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(interceptor, "internalToken", "test-secret");
    }

    @Test
    void missingHeader_shouldRejectWith401() throws Exception {
        MockHttpServletRequest request = requestWithout("X-Internal-Token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void wrongToken_shouldRejectWith401() throws Exception {
        MockHttpServletRequest request = requestWithout("X-Internal-Token");
        request.addHeader("X-Internal-Token", "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void matchingToken_shouldPass() throws Exception {
        MockHttpServletRequest request = requestWithout("X-Internal-Token");
        request.addHeader("X-Internal-Token", "test-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void unconfiguredToken_shouldPass_openByDefault() throws Exception {
        ReflectionTestUtils.setField(interceptor, "internalToken", "");
        MockHttpServletRequest request = requestWithout("X-Internal-Token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    private MockHttpServletRequest requestWithout(String headerName) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", API_PATH);
        request.setRequestURI(API_PATH);
        return request;
    }
}