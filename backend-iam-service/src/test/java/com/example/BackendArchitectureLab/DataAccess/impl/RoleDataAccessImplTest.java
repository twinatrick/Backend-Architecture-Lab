package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Repository.RoleRepository;
import com.example.BackendArchitectureLab.DataAccess.IRoleDataAccess;
import com.example.BackendArchitectureLab.Entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Example;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RoleDataAccessImpl.
 * Uses in-memory H2 database for testing.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(RoleDataAccessImpl.class)
class RoleDataAccessImplTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private IRoleDataAccess roleDataAccess;

    @BeforeEach
    void setUp() {
        roleRepository.deleteAll();
    }

    @Test
    @DisplayName("Should find roles by ids")
    void testFindRoleByIdIn() {
        // Arrange
        Role role1 = new Role();
        role1.setName("ROLE_ADMIN");
        roleRepository.save(role1);

        Role role2 = new Role();
        role2.setName("ROLE_USER");
        roleRepository.save(role2);

        List<UUID> ids = List.of(role1.getId(), role2.getId());

        // Act
        List<Role> result = roleDataAccess.findRoleByIdIn(ids);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> r.getName().equals("ROLE_ADMIN")));
        assertTrue(result.stream().anyMatch(r -> r.getName().equals("ROLE_USER")));
    }

    @Test
    @DisplayName("Should return empty list when no roles match ids")
    void testFindRoleByIdIn_NotFound() {
        // Arrange
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());

        // Act
        List<Role> result = roleDataAccess.findRoleByIdIn(ids);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should find subset of roles when only some ids match")
    void testFindRoleByIdIn_PartialMatch() {
        // Arrange
        Role role1 = new Role();
        role1.setName("ROLE_ADMIN");
        roleRepository.save(role1);

        List<UUID> ids = List.of(role1.getId(), UUID.randomUUID());

        // Act
        List<Role> result = roleDataAccess.findRoleByIdIn(ids);

        // Assert
        assertEquals(1, result.size());
        assertEquals("ROLE_ADMIN", result.get(0).getName());
    }

    @Test
    @DisplayName("Should handle empty id list")
    void testFindRoleByIdIn_EmptyList() {
        // Act
        List<Role> result = roleDataAccess.findRoleByIdIn(List.of());

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should check if role exists by example")
    void testExists() {
        // Arrange
        Role role = new Role();
        role.setName("ROLE_ADMIN");
        roleRepository.save(role);

        Role probe = new Role();
        probe.setName("ROLE_ADMIN");
        Example<Role> example = Example.of(probe);

        // Act
        boolean result = roleDataAccess.exists(example);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Should return false when role does not exist by example")
    void testExists_NotFound() {
        // Arrange
        Role probe = new Role();
        probe.setName("ROLE_NONEXISTENT");
        Example<Role> example = Example.of(probe);

        // Act
        boolean result = roleDataAccess.exists(example);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("Should save a role")
    void testSave() {
        // Arrange
        Role role = new Role();
        role.setName("ROLE_ADMIN");
        role.setDescription("Administrator role");

        // Act
        Role saved = roleDataAccess.save(role);

        // Assert
        assertNotNull(saved.getId());
        assertEquals("ROLE_ADMIN", saved.getName());
        assertEquals("Administrator role", saved.getDescription());
    }

    @Test
    @DisplayName("Should find all roles")
    void testFindAll() {
        // Arrange
        Role role1 = new Role();
        role1.setName("ROLE_ADMIN");
        roleRepository.save(role1);

        Role role2 = new Role();
        role2.setName("ROLE_USER");
        roleRepository.save(role2);

        // Act
        List<Role> result = roleDataAccess.findAll();

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should find role by id")
    void testFindById() {
        // Arrange
        Role role = new Role();
        role.setName("ROLE_ADMIN");
        Role saved = roleRepository.save(role);

        // Act
        Optional<Role> result = roleDataAccess.findById(saved.getId());

        // Assert
        assertTrue(result.isPresent());
        assertEquals("ROLE_ADMIN", result.get().getName());
    }

    @Test
    @DisplayName("Should return empty when role not found by id")
    void testFindById_NotFound() {
        // Act
        Optional<Role> result = roleDataAccess.findById(UUID.randomUUID());

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should delete a role")
    void testDelete() {
        // Arrange
        Role role = new Role();
        role.setName("ROLE_ADMIN");
        Role saved = roleRepository.save(role);
        UUID roleKey = saved.getId();

        // Act
        roleDataAccess.delete(saved);

        // Assert
        assertFalse(roleRepository.findById(roleKey).isPresent());
    }

    @Test
    @DisplayName("Should find all roles by ids")
    void testFindAllById() {
        // Arrange
        Role role1 = new Role();
        role1.setName("ROLE_ADMIN");
        roleRepository.save(role1);

        Role role2 = new Role();
        role2.setName("ROLE_USER");
        roleRepository.save(role2);

        List<UUID> ids = List.of(role1.getId(), role2.getId());

        // Act
        List<Role> result = roleDataAccess.findAllById(ids);

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should find role by name")
    void testFindRoleByName() {
        // Arrange
        Role role = new Role();
        role.setName("ROLE_ADMIN");
        roleRepository.save(role);

        // Act
        Role result = roleDataAccess.findRoleByName("ROLE_ADMIN");

        // Assert
        assertNotNull(result);
        assertEquals("ROLE_ADMIN", result.getName());
    }

    @Test
    @DisplayName("Should return null when role not found by name")
    void testFindRoleByName_NotFound() {
        // Act
        Role result = roleDataAccess.findRoleByName("ROLE_NONEXISTENT");

        // Assert
        assertNull(result);
    }
}

