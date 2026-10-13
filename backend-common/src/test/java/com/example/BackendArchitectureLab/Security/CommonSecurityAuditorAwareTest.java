package com.example.BackendArchitectureLab.Security;

import com.example.BackendArchitectureLab.Util.SecurityUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonSecurityAuditorAwareTest {

    @Mock
    private SecurityUtil securityUtil;

    @InjectMocks
    private CommonSecurityAuditorAware auditorAware;

    @Test
    @DisplayName("getCurrentAuditor 應委派至 SecurityUtil.getCurrentAuditor")
    void getCurrentAuditor_shouldDelegateToSecurityUtil() {
        when(securityUtil.getCurrentAuditor()).thenReturn(Optional.of("user-123"));

        Optional<String> result = auditorAware.getCurrentAuditor();

        assertTrue(result.isPresent());
        assertEquals("user-123", result.get());
        verify(securityUtil).getCurrentAuditor();
    }
}
