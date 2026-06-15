package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Repository.RoleRepository;
import com.example.BackendArchitectureLab.Repository.UserRepository;
import com.example.BackendArchitectureLab.Repository.UserRoleRepository;
import com.example.BackendArchitectureLab.DataAccess.IUserRoleDataAccess;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UserRoleDataAccessImpl.
 * Uses in-memory H2 database for testing.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(UserRoleDataAccessImpl.class)
class UserRoleDataAccessImplTest {

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private IUserRoleDataAccess userRoleDataAccess;

    @BeforeEach
    void setUp() {
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
    }

    @Test
    @DisplayName("Should delete user-role associations")
    void testDeleteAllByUserInAndRoleIn() {
        // Arrange
        User user1 = new User();
        user1.setEmail("user1@example.com");
        user1.setPassword("password");
        user1.setDisabled(false);
        userRepository.save(user1);

        Role role1 = new Role();
        role1.setName("ROLE_ADMIN");
        roleRepository.save(role1);

        UserRole userRole = new UserRole();
        userRole.setUser(user1);
        userRole.setRole(role1);
        userRoleRepository.save(userRole);

        // Verify association exists
        long countBefore = userRoleRepository.count();
        assertEquals(1, countBefore);

        // Act
        userRoleDataAccess.deleteAllByUserInAndRoleIn(List.of(user1), List.of(role1));

        // Assert
        long countAfter = userRoleRepository.count();
        assertEquals(0, countAfter);
    }

    @Test
    @DisplayName("Should delete multiple user-role associations")
    void testDeleteAllByUserInAndRoleIn_Multiple() {
        // Arrange
        User user1 = new User();
        user1.setEmail("user1@example.com");
        user1.setPassword("password");
        user1.setDisabled(false);
        userRepository.save(user1);

        User user2 = new User();
        user2.setEmail("user2@example.com");
        user2.setPassword("password");
        user2.setDisabled(false);
        userRepository.save(user2);

        Role role1 = new Role();
        role1.setName("ROLE_ADMIN");
        roleRepository.save(role1);

        Role role2 = new Role();
        role2.setName("ROLE_USER");
        roleRepository.save(role2);

        UserRole userRole1 = new UserRole();
        userRole1.setUser(user1);
        userRole1.setRole(role1);
        userRoleRepository.save(userRole1);

        UserRole userRole2 = new UserRole();
        userRole2.setUser(user2);
        userRole2.setRole(role2);
        userRoleRepository.save(userRole2);

        // Act
        userRoleDataAccess.deleteAllByUserInAndRoleIn(
                List.of(user1, user2),
                List.of(role1, role2)
        );

        // Assert
        long countAfter = userRoleRepository.count();
        assertEquals(0, countAfter);
    }

    @Test
    @DisplayName("Should not delete when no matching associations exist")
    void testDeleteAllByUserInAndRoleIn_NoMatch() {
        // Arrange
        User user1 = new User();
        user1.setEmail("user1@example.com");
        user1.setPassword("password");
        user1.setDisabled(false);
        userRepository.save(user1);

        Role role1 = new Role();
        role1.setName("ROLE_ADMIN");
        roleRepository.save(role1);

        UserRole userRole = new UserRole();
        userRole.setUser(user1);
        userRole.setRole(role1);
        userRoleRepository.save(userRole);

        // Create different user and role
        User user2 = new User();
        user2.setEmail("user2@example.com");
        user2.setPassword("password");
        user2.setDisabled(false);
        userRepository.save(user2);

        Role role2 = new Role();
        role2.setName("ROLE_USER");
        roleRepository.save(role2);

        // Act - try to delete with non-matching user/role
        userRoleDataAccess.deleteAllByUserInAndRoleIn(List.of(user2), List.of(role2));

        // Assert - original association should still exist
        long countAfter = userRoleRepository.count();
        assertEquals(1, countAfter);
    }

    @Test
    @DisplayName("Should handle empty lists gracefully")
    void testDeleteAllByUserInAndRoleIn_EmptyLists() {
        // Act & Assert - should not throw Exception
        assertDoesNotThrow(() ->
                userRoleDataAccess.deleteAllByUserInAndRoleIn(List.of(), List.of())
        );
    }

    @Test
    @DisplayName("Should find user roles by user id")
    void testFindByUserId() {
        User user = new User();
        user.setEmail("find@example.com");
        user.setPassword("password");
        user.setDisabled(false);
        userRepository.save(user);

        Role role = new Role();
        role.setName("ROLE_FIND");
        roleRepository.save(role);

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRoleRepository.save(userRole);

        List<UserRole> result = userRoleDataAccess.findByUserId(user.getId());
        assertEquals(1, result.size());
        assertEquals(user.getId(), result.get(0).getUser().getId());
    }

    @Test
    @DisplayName("Should delete all user roles by user id")
    void testDeleteByUserId() {
        User user = new User();
        user.setEmail("deleteall@example.com");
        user.setPassword("password");
        user.setDisabled(false);
        userRepository.save(user);

        Role role = new Role();
        role.setName("ROLE_DEL_ALL");
        roleRepository.save(role);

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRoleRepository.save(userRole);

        userRoleDataAccess.deleteByUserId(user.getId());

        assertTrue(userRoleDataAccess.findByUserId(user.getId()).isEmpty());
    }

    @Test
    @DisplayName("Should delete user role by user id and role id")
    void testDeleteByUserIdAndRoleId() {
        User user = new User();
        user.setEmail("deletepair@example.com");
        user.setPassword("password");
        user.setDisabled(false);
        userRepository.save(user);

        Role role1 = new Role();
        role1.setName("ROLE_DEL_PAIR_1");
        roleRepository.save(role1);

        Role role2 = new Role();
        role2.setName("ROLE_DEL_PAIR_2");
        roleRepository.save(role2);

        UserRole ur1 = new UserRole();
        ur1.setUser(user);
        ur1.setRole(role1);
        userRoleRepository.save(ur1);

        UserRole ur2 = new UserRole();
        ur2.setUser(user);
        ur2.setRole(role2);
        userRoleRepository.save(ur2);

        userRoleDataAccess.deleteByUserIdAndRoleId(user.getId(), role1.getId());

        List<UserRole> left = userRoleDataAccess.findByUserId(user.getId());
        assertEquals(1, left.size());
        assertEquals(role2.getId(), left.get(0).getRole().getId());
    }
}

