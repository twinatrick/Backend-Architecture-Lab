package com.example.BackendArchitectureLab.Security;

import com.example.BackendArchitectureLab.DataAccess.IUserDataAccess;
import com.example.BackendArchitectureLab.Entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private IUserDataAccess userDataAccess;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User testUser;
    private String email;

    @BeforeEach
    void setUp() {
        email = "test@example.com";
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail(email);
        testUser.setPassword("encodedPassword");
        testUser.setName("Test User");
    }

    @Test
    @DisplayName("Should load user by username successfully")
    void testLoadUserByUsername() {
        when(userDataAccess.findByEmail(email)).thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

        assertNotNull(userDetails);
        assertEquals(email, userDetails.getUsername());
        assertEquals("encodedPassword", userDetails.getPassword());
        verify(userDataAccess).findByEmail(email);
    }

    @Test
    @DisplayName("Should throw UsernameNotFoundException when user not found")
    void testLoadUserByUsername_NotFound() {
        when(userDataAccess.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername(email));
        verify(userDataAccess).findByEmail(email);
    }
}
