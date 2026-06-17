package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityUtilTest {

    @Mock
    private UserServiceFeignClient userServiceFeignClient;

    @InjectMocks
    private SecurityUtil securityUtil;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
}
