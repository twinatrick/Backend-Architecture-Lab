package com.example.BackendArchitectureLab.Filter;

import com.example.BackendArchitectureLab.Aop.CallTreeContext;
import com.example.BackendArchitectureLab.Aop.InvocationNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class DiagnosticTraceInterceptorTest {

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void tearDown() {
        CallTreeContext.clear();
    }

    @Test
    @DisplayName("當帶有 X-Diagnostic-Trace 標頭時應開啟診斷模式並於結束時清理 ThreadLocal")
    void doFilterInternal_WhenHeaderPresent_EnablesDiagnosticAndCleansUp() throws ServletException, IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        DiagnosticTraceInterceptor filter = new DiagnosticTraceInterceptor(objectMapper);
        ReflectionTestUtils.setField(filter, "globalDiagnosticEnabled", false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/infoVo");
        request.addHeader("X-Diagnostic-Trace", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean[] wasDiagnosticInside = new boolean[1];
        doAnswer(invocation -> {
            wasDiagnosticInside[0] = CallTreeContext.isDiagnostic();
            InvocationNode node = CallTreeContext.push("CONTROLLER", "UserController", "infoVo");
            if (node != null) {
                node.setDurationNanos(15_000_000L);
                CallTreeContext.pop(node, true, null);
            }
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertThat(wasDiagnosticInside[0]).isTrue();
        assertThat(CallTreeContext.isDiagnostic()).isFalse();
        assertThat(CallTreeContext.get()).isNull();
    }

    @Test
    @DisplayName("無標頭且未開啟全域設定時應保持關閉")
    void doFilterInternal_WhenNoHeader_DiagnosticDisabled() throws ServletException, IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        DiagnosticTraceInterceptor filter = new DiagnosticTraceInterceptor(objectMapper);
        ReflectionTestUtils.setField(filter, "globalDiagnosticEnabled", false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/infoVo");
        MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean[] wasDiagnosticInside = new boolean[1];
        doAnswer(invocation -> {
            wasDiagnosticInside[0] = CallTreeContext.isDiagnostic();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertThat(wasDiagnosticInside[0]).isFalse();
        assertThat(CallTreeContext.isDiagnostic()).isFalse();
    }
}
