package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Aop.DefaultPermissionValidator;
import com.example.BackendArchitectureLab.Aop.LocalPermissionValidator;
import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionValidatorConfigTest {

    @Mock
    private PermissionCheckFeignClient feignClient;

    @Mock
    private ObjectProvider<PermissionCheckFeignClient> feignClientProvider;

    @Mock
    private ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker circuitBreaker;

    private final PermissionValidatorConfig config = new PermissionValidatorConfig();

    @Test
    @DisplayName("當 PermissionCheckFeignClient 存在時，應成功建立 DefaultPermissionValidator 實例")
    void defaultPermissionValidator_withFeignClient_createsDefaultPermissionValidator() {
        when(feignClientProvider.getIfAvailable()).thenReturn(feignClient);
        when(circuitBreakerRegistryProvider.getIfAvailable()).thenReturn(circuitBreakerRegistry);
        when(circuitBreakerRegistry.circuitBreaker("permissionCheck")).thenReturn(circuitBreaker);

        LocalPermissionValidator validator = config.defaultPermissionValidator(feignClientProvider, circuitBreakerRegistryProvider);

        assertThat(validator).isNotNull();
        assertThat(validator).isInstanceOf(DefaultPermissionValidator.class);
        DefaultPermissionValidator defaultValidator = (DefaultPermissionValidator) validator;
        assertThat(defaultValidator.getCircuitBreaker()).isSameAs(circuitBreaker);
        verify(circuitBreakerRegistry).circuitBreaker("permissionCheck");
    }

    @Test
    @DisplayName("當 CircuitBreakerRegistry 不可用時，應以預設 CircuitBreaker 建立 DefaultPermissionValidator")
    void defaultPermissionValidator_withoutRegistry_createsDefaultPermissionValidatorWithDefaultBreaker() {
        when(feignClientProvider.getIfAvailable()).thenReturn(feignClient);
        when(circuitBreakerRegistryProvider.getIfAvailable()).thenReturn(null);

        LocalPermissionValidator validator = config.defaultPermissionValidator(feignClientProvider, circuitBreakerRegistryProvider);

        assertThat(validator).isNotNull();
        assertThat(validator).isInstanceOf(DefaultPermissionValidator.class);
        DefaultPermissionValidator defaultValidator = (DefaultPermissionValidator) validator;
        assertThat(defaultValidator.getCircuitBreaker()).isNotNull();
        assertThat(defaultValidator.getCircuitBreaker().getName()).isEqualTo("permissionCheck");
    }

    @Test
    @DisplayName("當 PermissionCheckFeignClient 不可用時，應回退為 Fail-Closed 驗證器，始終回傳 false")
    void defaultPermissionValidator_withoutFeignClient_returnsFailClosedFallback() {
        when(feignClientProvider.getIfAvailable()).thenReturn(null);

        LocalPermissionValidator validator = config.defaultPermissionValidator(feignClientProvider, circuitBreakerRegistryProvider);

        assertThat(validator).isNotNull();
        assertThat(validator).isNotInstanceOf(DefaultPermissionValidator.class);
        assertThat(validator.validate("admin@example.com", "ExternalApi", "BotConfig", "View")).isFalse();
        assertThat(validator.validate("test@example.com", "Competency", "Skill", "Edit")).isFalse();
    }
}
