package com.example.BackendArchitectureLab.Aop;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionCheckTest {

    @Mock
    private LocalPermissionValidator localPermissionValidator;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private PermissionCheck permissionCheck;
    private MockHttpServletResponse httpServletResponse;

    static class SampleController {
        @RequirePermission("View")
        public String viewEndpoint() {
            return "ok";
        }
    }

    @BeforeEach
    void setUp() {
        permissionCheck = new PermissionCheck(localPermissionValidator);
        ReflectionTestUtils.setField(permissionCheck, "applicationName", "competency-service");

        httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, httpServletResponse));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("當 CircuitBreaker OPEN 拋出 CallNotPermittedException 時，應回傳 503 CIRCUIT_BREAKER_OPEN")
    void around_circuitBreakerOpen_returns503CircuitBreakerOpen() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@example.com", "credentials", Collections.emptyList()));

        SampleController controller = new SampleController();
        Method method = SampleController.class.getMethod("viewEndpoint");
        RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);

        when(joinPoint.getTarget()).thenReturn(controller);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.toShortString()).thenReturn("SampleController.viewEndpoint()");
        when(methodSignature.getMethod()).thenReturn(method);

        CircuitBreaker cb = CircuitBreaker.of("test", CircuitBreakerConfig.ofDefaults());
        CallNotPermittedException exception = CallNotPermittedException.createCallNotPermittedException(cb);

        when(localPermissionValidator.validate("user@example.com", "Competency", "Sample", "View"))
                .thenThrow(exception);

        Object response = permissionCheck.around(joinPoint);

        assertThat(response).isInstanceOf(ResponseType.class);
        ResponseType<?> fail = (ResponseType<?>) response;
        assertThat(fail.getErrorType()).isEqualTo("CIRCUIT_BREAKER_OPEN");
        assertThat(fail.getCode()).isEqualTo(503);
        assertThat(httpServletResponse.getStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("當權限驗證通過時，應順利執行目標方法")
    void around_permissionMatched_proceedsSuccessfully() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@example.com", "credentials", Collections.emptyList()));

        SampleController controller = new SampleController();
        Method method = SampleController.class.getMethod("viewEndpoint");

        when(joinPoint.getTarget()).thenReturn(controller);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.toShortString()).thenReturn("SampleController.viewEndpoint()");
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn("success-payload");

        when(localPermissionValidator.validate("admin@example.com", "Competency", "Sample", "View"))
                .thenReturn(true);

        Object response = permissionCheck.around(joinPoint);

        assertThat(response).isEqualTo("success-payload");
        verify(joinPoint).proceed();
    }
}
