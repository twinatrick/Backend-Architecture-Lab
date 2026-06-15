package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Search.UserSearchQuery;
import com.example.BackendArchitectureLab.Repository.UserRepository;
import com.example.BackendArchitectureLab.DataAccess.IUserDataAccess;
import com.example.BackendArchitectureLab.Entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UserDataAccessImpl.
 * Uses in-memory H2 database for testing.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(UserDataAccessImpl.class)
class UserDataAccessImplTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IUserDataAccess userDataAccess;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save user successfully")
    void testSave() {
        // Arrange
        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("hashedPassword");
        user.setDisabled(false);

        // Act
        userDataAccess.save(user);

        // Assert
        List<User> users = userRepository.findAll();
        assertEquals(1, users.size());
        assertEquals("test@example.com", users.get(0).getEmail());
    }

    @Test
    @DisplayName("Should find all users")
    void testFindAll() {
        // Arrange
        User user1 = new User();
        user1.setEmail("user1@example.com");
        user1.setPassword("password1");
        user1.setDisabled(false);

        User user2 = new User();
        user2.setEmail("user2@example.com");
        user2.setPassword("password2");
        user2.setDisabled(false);

        userRepository.save(user1);
        userRepository.save(user2);

        // Act
        List<User> result = userDataAccess.findAll();

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should find user by email")
    void testFindByEmail() {
        // Arrange
        User user = new User();
        user.setEmail("find@example.com");
        user.setPassword("hashedPassword");
        user.setDisabled(false);
        userRepository.save(user);

        // Act
        Optional<User> result = userDataAccess.findByEmail("find@example.com");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("find@example.com", result.get().getEmail());
    }

    @Test
    @DisplayName("Should return empty when email not found")
    void testFindByEmail_NotFound() {
        // Act
        Optional<User> result = userDataAccess.findByEmail("nonexistent@example.com");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should update existing user")
    void testSave_Update() {
        // Arrange
        User user = new User();
        user.setEmail("update@example.com");
        user.setPassword("oldPassword");
        user.setDisabled(false);
        userRepository.save(user);

        // Get saved user
        User savedUser = userRepository.findByEmail("update@example.com").orElseThrow();
        UUID userId = savedUser.getId();

        // Modify user
        savedUser.setPassword("newPassword");

        // Act
        userDataAccess.save(savedUser);

        // Assert
        User updatedUser = userRepository.findByEmail("update@example.com").orElseThrow();
        assertEquals("newPassword", updatedUser.getPassword());
        assertEquals(userId, updatedUser.getId()); // Same id
    }

    @Test
    @DisplayName("Should find user by id")
    void testFindById() {
        // Arrange
        User user = new User();
        user.setEmail("findbyid@example.com");
        user.setPassword("password");
        user.setDisabled(false);
        User saved = userRepository.save(user);

        // Act
        Optional<User> result = userDataAccess.findById(saved.getId());

        // Assert
        assertTrue(result.isPresent());
        assertEquals("findbyid@example.com", result.get().getEmail());
    }

    @Test
    @DisplayName("Should return empty when user not found by id")
    void testFindById_NotFound() {
        // Act
        Optional<User> result = userDataAccess.findById(UUID.randomUUID());

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should find all users by ids")
    void testFindAllById() {
        // Arrange
        User user1 = new User();
        user1.setEmail("user1@example.com");
        user1.setPassword("password1");
        user1.setDisabled(false);
        User saved1 = userRepository.save(user1);

        User user2 = new User();
        user2.setEmail("user2@example.com");
        user2.setPassword("password2");
        user2.setDisabled(false);
        User saved2 = userRepository.save(user2);

        User user3 = new User();
        user3.setEmail("user3@example.com");
        user3.setPassword("password3");
        user3.setDisabled(false);
        userRepository.save(user3);

        // Act
        List<User> result = userDataAccess.findAllById(List.of(saved1.getId(), saved2.getId()));

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(u -> u.getEmail().equals("user1@example.com")));
        assertTrue(result.stream().anyMatch(u -> u.getEmail().equals("user2@example.com")));
    }

    @Test
    @DisplayName("Should return empty list when no users found by ids")
    void testFindAllById_NotFound() {
        // Act
        List<User> result = userDataAccess.findAllById(List.of(UUID.randomUUID(), UUID.randomUUID()));

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should search users with query")
    void testSearchUsers() {
        // Arrange
        User user1 = new User();
        user1.setName("John Doe");
        user1.setEmail("john@example.com");
        user1.setPassword("password1");
        user1.setDisabled(false);
        userRepository.save(user1);

        User user2 = new User();
        user2.setName("Jane Smith");
        user2.setEmail("jane@example.com");
        user2.setPassword("password2");
        user2.setDisabled(false);
        userRepository.save(user2);

        UserSearchQuery query = new UserSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("name");
        query.setSortDir("asc");
        query.setName("John");

        // Act
        Page<User> result = userDataAccess.searchUsers(query);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals("John Doe", result.getContent().get(0).getName());
    }

    @Test
    @DisplayName("Should search users with email Filter")
    void testSearchUsers_WithEmail() {
        // Arrange
        User user1 = new User();
        user1.setName("John");
        user1.setEmail("john@example.com");
        user1.setPassword("password1");
        user1.setDisabled(false);
        userRepository.save(user1);

        User user2 = new User();
        user2.setName("Jane");
        user2.setEmail("jane@example.com");
        user2.setPassword("password2");
        user2.setDisabled(false);
        userRepository.save(user2);

        UserSearchQuery query = new UserSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("email");
        query.setSortDir("desc");
        query.setEmail("john");

        // Act
        Page<User> result = userDataAccess.searchUsers(query);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals("john@example.com", result.getContent().get(0).getEmail());
    }

    @Test
    @DisplayName("Should search users with disabled Filter")
    void testSearchUsers_WithDisabled() {
        // Arrange
        User user1 = new User();
        user1.setName("Active User");
        user1.setEmail("active@example.com");
        user1.setPassword("password1");
        user1.setDisabled(false);
        userRepository.save(user1);

        User user2 = new User();
        user2.setName("Disabled User");
        user2.setEmail("disabled@example.com");
        user2.setPassword("password2");
        user2.setDisabled(true);
        userRepository.save(user2);

        UserSearchQuery query = new UserSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("name");
        query.setSortDir("asc");
        query.setDisabled(true);

        // Act
        Page<User> result = userDataAccess.searchUsers(query);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals("Disabled User", result.getContent().get(0).getName());
    }

    @Test
    @DisplayName("Should search users with pagination")
    void testSearchUsers_Pagination() {
        // Arrange
        for (int i = 0; i < 5; i++) {
            User user = new User();
            user.setName("User " + i);
            user.setEmail("user" + i + "@example.com");
            user.setPassword("password" + i);
            user.setDisabled(false);
            userRepository.save(user);
        }

        UserSearchQuery query = new UserSearchQuery();
        query.setPage(0);
        query.setSize(2);
        query.setSortBy("name");
        query.setSortDir("asc");

        // Act
        Page<User> result = userDataAccess.searchUsers(query);

        // Assert
        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertEquals(2, result.getContent().size());
    }
}

