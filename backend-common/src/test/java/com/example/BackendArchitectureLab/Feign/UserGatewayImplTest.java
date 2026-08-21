package com.example.BackendArchitectureLab.Feign;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGatewayImplTest {

    @Mock
    private UserServiceFeignClient userServiceFeignClient;

    private UserGatewayImpl userGateway;

    @BeforeEach
    void setUp() {
        userGateway = new UserGatewayImpl(Optional.of(userServiceFeignClient));
    }

    @Test
    void existsUserById_shouldReturnTrue_whenUserExists() {
        UUID userId = UUID.randomUUID();
        when(userServiceFeignClient.existsUserById(userId)).thenReturn(true);

        boolean exists = userGateway.existsUserById(userId);

        assertTrue(exists);
        verify(userServiceFeignClient).existsUserById(userId);
    }

    @Test
    void existsUserById_shouldReturnFalse_whenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userServiceFeignClient.existsUserById(userId)).thenReturn(false);

        boolean exists = userGateway.existsUserById(userId);

        assertFalse(exists);
        verify(userServiceFeignClient).existsUserById(userId);
    }

    @Test
    void existsUserById_shouldThrowException_whenFeignClientNotAvailable() {
        UserGatewayImpl unavailableGateway = new UserGatewayImpl(Optional.empty());
        UUID userId = UUID.randomUUID();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> unavailableGateway.existsUserById(userId)
        );

        assertEquals("UserServiceFeignClient is not available in this service", exception.getMessage());
    }

    @Test
    void existsUserById_shouldThrowException_whenOptionalIsNull() {
        UserGatewayImpl unavailableGateway = new UserGatewayImpl(null);
        UUID userId = UUID.randomUUID();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> unavailableGateway.existsUserById(userId)
        );

        assertEquals("UserServiceFeignClient is not available in this service", exception.getMessage());
    }
}
