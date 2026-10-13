package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Security.AuthenticatedUser;
import com.example.BackendArchitectureLab.Security.JwtAuthenticationDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityUtilTest {

    @Mock
    private UserServiceFeignClient userServiceFeignClient;

    private SecurityUtil securityUtil;

    @BeforeEach
    void setUp() {
        securityUtil = new SecurityUtil(Optional.of(userServiceFeignClient));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireCurrentUserId_shouldThrowException_whenFeignClientNotAvailable() {
        SecurityUtil unavailableUtil = new SecurityUtil(Optional.empty());
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new Object());
        when(auth.getName()).thenReturn("test@example.com");
        SecurityContextHolder.getContext().setAuthentication(auth);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                unavailableUtil::requireCurrentUserId);

        assertEquals(
                "Current user not found - UserServiceFeignClient is not available in this service",
                ex.getMessage());
    }

    @Test
    void requireCurrentUserId_shouldThrowException_whenAuthIsNull() {
        SecurityContextHolder.getContext().setAuthentication(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> securityUtil.requireCurrentUserId());

        assertEquals("Current user not found - no authentication", ex.getMessage());
    }

    @Test
    void requireCurrentUserId_shouldThrowException_whenPrincipalIsNull() {
        Authentication auth = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(auth);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> securityUtil.requireCurrentUserId());

        assertEquals("Current user not found - no authentication", ex.getMessage());
    }

    @Test
    void requireCurrentUserId_shouldThrowException_whenEmailIsNull() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new Object());
        SecurityContextHolder.getContext().setAuthentication(auth);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> securityUtil.requireCurrentUserId());

        assertEquals("Current user not found - no email in authentication", ex.getMessage());
    }

    @Test
    void requireCurrentUserId_shouldThrowException_whenEmailIsBlank() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new Object());
        when(auth.getName()).thenReturn("   ");
        SecurityContextHolder.getContext().setAuthentication(auth);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> securityUtil.requireCurrentUserId());

        assertEquals("Current user not found - no email in authentication", ex.getMessage());
    }

    @Test
    void requireCurrentUserId_shouldThrowException_whenUserVoIsNull() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new Object());
        when(auth.getName()).thenReturn("test@example.com");
        SecurityContextHolder.getContext().setAuthentication(auth);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> securityUtil.requireCurrentUserId());

        assertEquals("Current user not found - user lookup failed", ex.getMessage());
        verify(userServiceFeignClient).getUserByEmail("test@example.com");
    }

    @Test
    void requireCurrentUserId_shouldThrowException_whenUserIdIsNull() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new Object());
        when(auth.getName()).thenReturn("test@example.com");
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserVo userVo = new UserVo();
        when(userServiceFeignClient.getUserByEmail("test@example.com")).thenReturn(userVo);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> securityUtil.requireCurrentUserId());

        assertEquals("Current user not found - user lookup failed", ex.getMessage());
    }

    @Test
    void requireCurrentUserId_shouldReturnUuid_whenValid() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new Object());
        when(auth.getName()).thenReturn("test@example.com");
        SecurityContextHolder.getContext().setAuthentication(auth);

        UUID expectedUuid = UUID.randomUUID();
        UserVo userVo = new UserVo();
        userVo.setId(expectedUuid.toString());
        when(userServiceFeignClient.getUserByEmail("test@example.com")).thenReturn(userVo);

        UUID result = securityUtil.requireCurrentUserId();

        assertEquals(expectedUuid, result);
    }

    @Test
    void requireCurrentUserId_shouldReturnUuidDirectly_whenPrincipalIsAuthenticatedUser() {
        UUID expectedUuid = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser("test@example.com", expectedUuid.toString(), List.of());
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(auth);

        UUID result = securityUtil.requireCurrentUserId();

        assertEquals(expectedUuid, result);
        verifyNoInteractions(userServiceFeignClient);
    }

    @Test
    void requireCurrentUserId_shouldReturnUuidDirectly_whenDetailsIsJwtAuthenticationDetails() {
        UUID expectedUuid = UUID.randomUUID();
        JwtAuthenticationDetails details = mock(JwtAuthenticationDetails.class);
        when(details.getUserId()).thenReturn(expectedUuid.toString());

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new Object());
        when(auth.getDetails()).thenReturn(details);
        SecurityContextHolder.getContext().setAuthentication(auth);

        UUID result = securityUtil.requireCurrentUserId();

        assertEquals(expectedUuid, result);
        verifyNoInteractions(userServiceFeignClient);
    }

    @Test
    void getCurrentAuditor_shouldReturnEmpty_whenNoAuthentication() {
        SecurityContextHolder.clearContext();
        Optional<String> auditor = securityUtil.getCurrentAuditor();
        assertTrue(auditor.isEmpty());
    }

    @Test
    void getCurrentAuditor_shouldReturnUserId_whenAuthenticatedUserPresent() {
        UUID expectedUuid = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser("test@example.com", expectedUuid.toString(), List.of());
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<String> auditor = securityUtil.getCurrentAuditor();
        assertTrue(auditor.isPresent());
        assertEquals(expectedUuid.toString(), auditor.get());
    }

    @Test
    void getCurrentAuditor_shouldFallbackToName_whenUserIdThrowsException() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new Object());
        when(auth.getName()).thenReturn("fallback-user@example.com");
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<String> auditor = securityUtil.getCurrentAuditor();
        assertTrue(auditor.isPresent());
        assertEquals("fallback-user@example.com", auditor.get());
    }
}
