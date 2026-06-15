package com.example.BackendArchitectureLab.Config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class GatewaySecurityConfigTest {

    @Test
    @DisplayName("Should be annotated as @Configuration")
    void testConfigurationAnnotation() {
        assertTrue(GatewaySecurityConfig.class.isAnnotationPresent(Configuration.class));
    }

    @Test
    @DisplayName("Should declare @Bean for SecurityWebFilterChain")
    void testSecurityWebFilterChainBeanDeclaration() throws Exception {
        Method method = GatewaySecurityConfig.class.getDeclaredMethod("securityWebFilterChain",
                org.springframework.security.config.web.server.ServerHttpSecurity.class);
        assertNotNull(method);
        assertTrue(method.isAnnotationPresent(Bean.class));
        assertEquals(SecurityWebFilterChain.class, method.getReturnType());
    }
}
