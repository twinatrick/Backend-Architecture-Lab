package com.example.BackendArchitectureLab.Aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FourTierTelemetryAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private FourTierTelemetryAspect aspect;

    interface DummyUserRepository extends JpaRepository<Object, Long> {
    }

    abstract static class DummyUserRepoStub implements DummyUserRepository {
    }

    @BeforeEach
    void setUp() {
        aspect = new FourTierTelemetryAspect();
    }

    @AfterEach
    void tearDown() {
        CallTreeContext.clear();
    }

    @Test
    @DisplayName("未開啟診斷模式時切面應直接執行 proceed，不額外追蹤")
    void profile_WhenDiagnosticDisabled_ProceedsDirectly() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.profileController(joinPoint);

        assertThat(result).isEqualTo("result");
        verify(joinPoint).proceed();
        assertThat(CallTreeContext.finalizeAndGetRoot()).isNull();
    }

    @Test
    @DisplayName("開啟診斷模式時應正確攔截 Service 方法並記錄至 CallTree")
    void profile_WhenDiagnosticEnabled_ProfilesServiceLayer() throws Throwable {
        CallTreeContext.init(true);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringType()).thenReturn((Class) SampleService.class);
        when(methodSignature.getName()).thenReturn("executeBusinessLogic");
        when(joinPoint.proceed()).thenReturn("service-done");

        Object result = aspect.profileService(joinPoint);

        assertThat(result).isEqualTo("service-done");
        InvocationNode root = CallTreeContext.finalizeAndGetRoot();
        assertThat(root).isNotNull();
        assertThat(root.getLayer()).isEqualTo("SERVICE");
        assertThat(root.getClassName()).isEqualTo("SampleService");
        assertThat(root.getMethodName()).isEqualTo("executeBusinessLogic");
        assertThat(root.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("當目標方法拋出例外時應標記 ERROR 並原樣拋出例外")
    void profile_WhenMethodThrows_RecordsErrorAndRethrows() throws Throwable {
        CallTreeContext.init(true);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringType()).thenReturn((Class) SampleService.class);
        when(methodSignature.getName()).thenReturn("failedOperation");
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("Business conflict"));

        assertThatThrownBy(() -> aspect.profileService(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Business conflict");

        InvocationNode root = CallTreeContext.finalizeAndGetRoot();
        assertThat(root).isNotNull();
        assertThat(root.getStatus()).isEqualTo("ERROR");
        assertThat(root.getErrorMessage()).isEqualTo("Business conflict");
    }

    static class SampleService {
        public String executeBusinessLogic() {
            return "service-done";
        }
    }
}
