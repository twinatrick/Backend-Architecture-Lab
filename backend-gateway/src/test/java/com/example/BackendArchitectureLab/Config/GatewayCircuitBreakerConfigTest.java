package com.example.BackendArchitectureLab.Config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class GatewayCircuitBreakerConfigTest {

    @Test
    @DisplayName("Should be annotated as @Configuration")
    void testConfigurationAnnotation() {
        assertTrue(GatewayCircuitBreakerConfig.class.isAnnotationPresent(Configuration.class));
    }

    @Test
    @DisplayName("Should declare @Bean for Customizer<ReactiveResilience4JCircuitBreakerFactory>")
    void testCircuitBreakerCustomizerBeanDeclaration() throws Exception {
        Method method = GatewayCircuitBreakerConfig.class.getDeclaredMethod("defaultCustomizer");
        assertNotNull(method);
        assertTrue(method.isAnnotationPresent(Bean.class));
        assertEquals(Customizer.class, method.getReturnType());
    }

    @Test
    @DisplayName("Customizer should configure factory successfully")
    void testCustomizerExecution() {
        GatewayCircuitBreakerConfig config = new GatewayCircuitBreakerConfig();
        Customizer<ReactiveResilience4JCircuitBreakerFactory> customizer = config.defaultCustomizer();
        assertNotNull(customizer);

        ReactiveResilience4JCircuitBreakerFactory factory = new ReactiveResilience4JCircuitBreakerFactory(
                CircuitBreakerRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults()
        );
        assertDoesNotThrow(() -> customizer.customize(factory));
    }
}
