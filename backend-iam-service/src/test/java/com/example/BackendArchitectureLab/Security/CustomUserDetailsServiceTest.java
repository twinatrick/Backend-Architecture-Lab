package com.example.BackendArchitectureLab.Security;

import com.example.BackendArchitectureLab.DataAccess.IUserDataAccess;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
        when(userDataAccess.findByEmailWithRoles(email)).thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

        assertNotNull(userDetails);
        assertEquals(email, userDetails.getUsername());
        assertEquals("encodedPassword", userDetails.getPassword());
        verify(userDataAccess).findByEmailWithRoles(email);
    }

    @Test
    @DisplayName("Should load user by username and resolve authorities correctly")
    void testLoadUserByUsername_WithRoles_ResolvesAuthorities() {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("admin");

        UserRole userRole = new UserRole();
        userRole.setUser(testUser);
        userRole.setRole(role);

        List<UserRole> roles = new ArrayList<>();
        roles.add(userRole);
        testUser.setRoles(roles);

        when(userDataAccess.findByEmailWithRoles(email)).thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

        assertNotNull(userDetails);
        assertEquals(email, userDetails.getUsername());
        Set<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertTrue(authorities.contains("ROLE_ADMIN"));
        verify(userDataAccess).findByEmailWithRoles(email);
    }

    @Test
    @DisplayName("Should throw UsernameNotFoundException when user not found")
    void testLoadUserByUsername_NotFound() {
        when(userDataAccess.findByEmailWithRoles(email)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername(email));
        verify(userDataAccess).findByEmailWithRoles(email);
    }
}
