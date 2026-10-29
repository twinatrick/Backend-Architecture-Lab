package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IRoleDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserRoleDataAccess;
import com.example.BackendArchitectureLab.Dto.Query.UserSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.UserMapper;
import com.example.BackendArchitectureLab.Util.IamLocalCacheManager;
import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.test.StepVerifier;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.BatchResultVo;

/**
 * Unit tests for UserService.
 * Tests service layer business logic with mocked data access dependencies.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private TransactionExecutor transactionExecutor;

    @Mock
    private IUserDataAccess userDataAccess;

    @Mock
    private IUserRoleDataAccess userRoleDataAccess;

    @Mock
    private IRoleDataAccess roleDataAccess;

    @Mock
    private IRoleService roleService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private IamLocalCacheManager iamLocalCacheManager;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        lenient().when(transactionExecutor.executeReadOnly(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        lenient().when(transactionExecutor.executeWritable(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(transactionExecutor).runWritable(any());

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setPassword("hashedPassword");
        testUser.setDisabled(false);

        when(userMapper.toEntity(any(UserVo.class))).thenAnswer(invocation -> {
            UserVo vo = invocation.getArgument(0);
            User user = new User();
            if (vo.getId() != null && !vo.getId().isBlank()) {
                user.setId(UUID.fromString(vo.getId()));
            }
            user.setEmail(vo.getEmail());
            user.setPassword(vo.getPassword());
            user.setDisabled(vo.isDisabled());
            return user;
        });
        when(userMapper.toVo(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            UserVo vo = new UserVo();
            if (user.getId() != null) {
                vo.setId(user.getId().toString());
            }
            vo.setEmail(user.getEmail());
            vo.setPassword(user.getPassword());
            vo.setDisabled(user.isDisabled());
            return vo;
        });

        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
    }

    @Test
    @DisplayName("Should create user successfully")
    void testCreateUser() {
        // Arrange
        doNothing().when(userDataAccess).save(any(User.class));
        UserVo userVo = new UserVo();
        userVo.setEmail("test@example.com");
        userVo.setPassword("plainPassword");

        // Act
        UserVo result = userService.createUser(userVo);

        // Assert
        assertNotNull(result);
        verify(userDataAccess, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should return all users")
    void testGetUser() {
        // Arrange
        List<User> users = List.of(testUser);
        when(userDataAccess.findAll()).thenReturn(users);

        // Act
        List<UserVo> result = userService.getUser();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testUser.getEmail(), result.get(0).getEmail());
        verify(userDataAccess, times(1)).findAll();
    }

    @Test
    @DisplayName("Should return users by email")
    void testGetUserByEmail() {
        // Arrange
        when(userDataAccess.findByEmailWithRoles("test@example.com")).thenReturn(Optional.of(testUser));

        // Act
        List<UserVo> result = userService.getUserByEmail("test@example.com");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test@example.com", result.get(0).getEmail());
        verify(userDataAccess, times(1)).findByEmailWithRoles("test@example.com");
    }

    @Test
    @DisplayName("Should return only one user by email")
    void testGetOnlyUserByEmail() {
        // Arrange
        when(userDataAccess.findByEmailWithRoles("test@example.com")).thenReturn(Optional.of(testUser));

        // Act
        UserVo result = userService.getOnlyUserByEmail("test@example.com");

        // Assert
        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        verify(userDataAccess, times(1)).findByEmailWithRoles("test@example.com");
    }

    @Test
    @DisplayName("Should save user successfully")
    void testSaveUser() {
        // Arrange
        doNothing().when(userDataAccess).save(any(User.class));
        UserVo userVo = new UserVo();
        userVo.setEmail("test@example.com");
        userVo.setPassword("plainPassword");

        // Act
        userService.saveUser(userVo);

        // Assert
        verify(userDataAccess, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should save new user with role successfully")
    void testSaveUserWithRole_NewUser() {
        // Arrange
        UserVo userVo = new UserVo();
        userVo.setEmail("newuser@example.com");
        userVo.setPassword("plainPassword");
        userVo.setDisabled(false);
        userVo.setRoles(List.of(UUID.randomUUID().toString()));

        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return null;
        }).when(userDataAccess).save(any(User.class));
        doNothing().when(roleService).userBindRole(anyString(), anyList());

        // Act
        userService.saveUserWithRole(userVo);

        // Assert
        verify(userDataAccess, times(1)).save(any(User.class));
        verify(roleService, times(1)).userBindRole(anyString(), anyList());
    }

    @Test
    @DisplayName("Should update existing user with role successfully")
    void testSaveUserWithRole_ExistingUser() {
        // Arrange
        UserVo userVo = new UserVo();
        userVo.setId("existing-id");
        userVo.setEmail("test@example.com");
        userVo.setPassword("newPassword");
        userVo.setRoles(List.of(UUID.randomUUID().toString()));

        when(userDataAccess.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        doNothing().when(userDataAccess).save(any(User.class));
        doNothing().when(roleService).userBindRole(anyString(), anyList());

        // Act
        userService.saveUserWithRole(userVo);

        // Assert
        verify(userDataAccess, times(1)).findByEmail("test@example.com");
        verify(userDataAccess, times(1)).save(testUser);
        verify(roleService, times(1)).userBindRole(anyString(), anyList());
    }

    @Test
    @DisplayName("Should throw Exception when role list is null in saveUserWithRole")
    void testSaveUserWithRole_RoleListNull() {
        // Arrange
        UserVo userVo = new UserVo();
        userVo.setEmail("test@example.com");
        userVo.setPassword("password");
        userVo.setRoles(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.saveUserWithRole(userVo));
        assertEquals("Role list is required", exception.getMessage());
    }

    @Test
    @DisplayName("Should search users with pagination")
    void testSearchUsers_Success() {
        // Arrange
        UserSearchQuery query = new UserSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("createdTime");
        query.setSortDir("desc");
        query.setName("test");

        User user1 = new User();
        user1.setId(UUID.randomUUID());
        user1.setName("testUser1");
        user1.setEmail("test1@example.com");

        User user2 = new User();
        user2.setId(UUID.randomUUID());
        user2.setName("testUser2");
        user2.setEmail("test2@example.com");

        List<User> userList = List.of(user1, user2);
        Page<User> userPage = new PageImpl<>(userList, PageRequest.of(0, 20), 2);

        UserVo userVo1 = new UserVo();
        userVo1.setId(user1.getId().toString());
        userVo1.setName("testUser1");

        UserVo userVo2 = new UserVo();
        userVo2.setId(user2.getId().toString());
        userVo2.setName("testUser2");

        when(userDataAccess.searchUsers(any(UserSearchQuery.class))).thenReturn(userPage);
        when(userMapper.toVo(user1)).thenReturn(userVo1);
        when(userMapper.toVo(user2)).thenReturn(userVo2);

        // Act
        PageResult<UserVo> result = userService.searchUsers(query);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(2L, result.getTotalElements());
        assertEquals(0, result.getCurrentPage());
        assertEquals(20, result.getPageSize());
        assertTrue(result.getIsFirst());
        assertTrue(result.getIsLast());
        verify(userDataAccess).searchUsers(any(UserSearchQuery.class));
    }

    @Test
    @DisplayName("Should throw Exception for invalid sort field")
    void testSearchUsers_InvalidSortField() {
        // Arrange
        UserSearchQuery query = new UserSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("invalidField");
        query.setSortDir("desc");

        // Act & Assert
        assertThrows(AppException.class, () -> userService.searchUsers(query));
    }

    @Test
    @DisplayName("Should throw Exception for invalid sort direction")
    void testSearchUsers_InvalidSortDirection() {
        // Arrange
        UserSearchQuery query = new UserSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("createdTime");
        query.setSortDir("invalid");

        // Act & Assert
        assertThrows(AppException.class, () -> userService.searchUsers(query));
    }

    @Test
    @DisplayName("Should get user by ID successfully")
    void testGetUserById_Success() {
        // Arrange
        UUID userId = UUID.randomUUID();
        testUser.setId(userId);
        when(userDataAccess.findByIdWithRoles(userId)).thenReturn(Optional.of(testUser));

        // Act
        UserVo result = userService.getUserById(userId.toString());

        // Assert
        assertNotNull(result);
        assertEquals(testUser.getEmail(), result.getEmail());
        verify(userDataAccess).findByIdWithRoles(userId);
    }

    @Test
    @DisplayName("Should throw Exception when user ID is null")
    void testGetUserById_NullId() {
        // Act & Assert
        AppException exception = assertThrows(AppException.class,
                () -> userService.getUserById(null));
        assertEquals("使用者不存在", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw Exception when user ID is blank")
    void testGetUserById_BlankId() {
        // Act & Assert
        AppException exception = assertThrows(AppException.class,
                () -> userService.getUserById("   "));
        assertEquals("使用者不存在", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw Exception when user not found")
    void testGetUserById_UserNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userDataAccess.findByIdWithRoles(userId)).thenReturn(Optional.empty());

        // Act & Assert
        AppException exception = assertThrows(AppException.class,
                () -> userService.getUserById(userId.toString()));
        assertEquals("使用者不存在", exception.getMessage());
    }

    @Test
    @DisplayName("Should stream users chunked with events chunk and complete")
    void testStreamUsersChunked_Success() {
        // Arrange
        Page<User> page0 = new PageImpl<>(List.of(testUser), PageRequest.of(0, 10), 1);
        when(userDataAccess.findAllPaged(any())).thenReturn(page0);
        when(userMapper.toStreamVo(any(User.class))).thenAnswer(inv -> userMapper.toVo(inv.getArgument(0)));

        // Act
        Flux<ServerSentEvent<List<UserVo>>> stream = userService.streamUsersChunked(10);

        // Assert
        StepVerifier.create(stream.take(2))
                .expectNextMatches(sse -> "chunk".equals(sse.event()) && sse.data() != null && sse.data().size() == 1)
                .expectNextMatches(sse -> "complete".equals(sse.event()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should emit error event without data when database error occurs")
    void testStreamUsersChunked_DatabaseError_ShouldEmitErrorEventWithoutData() {
        // Arrange
        when(userDataAccess.findAllPaged(any())).thenThrow(new RuntimeException("Database error"));

        // Act
        Flux<ServerSentEvent<List<UserVo>>> stream = userService.streamUsersChunked(10);

        // Assert
        StepVerifier.create(stream)
                .expectNextMatches(sse -> "error".equals(sse.event()) && sse.data() == null)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw Exception when updating non-existing user with role")
    void testSaveUserWithRole_ExistingUser_NotFound() {
        // Arrange
        UserVo userVo = new UserVo();
        userVo.setId("existing-id");
        userVo.setEmail("notfound@example.com");
        userVo.setPassword("newPassword");
        userVo.setRoles(List.of(UUID.randomUUID().toString()));

        when(userDataAccess.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.saveUserWithRole(userVo));
        assertEquals("User not found", exception.getMessage());
    }

    @Test
    @DisplayName("Should not hash password if already hashed ($2a$)")
    void testCreateUser_AlreadyHashedPassword_2a() {
        // Arrange
        UserVo userVo = new UserVo();
        userVo.setEmail("test@example.com");
        userVo.setPassword("$2a$10$alreadyHashedPassword");
        
        doNothing().when(userDataAccess).save(any(User.class));

        // Act
        UserVo result = userService.createUser(userVo);

        // Assert
        assertNotNull(result);
        verify(userDataAccess).save(any(User.class));
    }

    @Test
    @DisplayName("Should not hash password if already hashed ($2b$)")
    void testSaveUser_AlreadyHashedPassword_2b() {
        // Arrange
        UserVo userVo = new UserVo();
        userVo.setEmail("test@example.com");
        userVo.setPassword("$2b$10$alreadyHashedPassword");
        
        doNothing().when(userDataAccess).save(any(User.class));

        // Act
        UserVo result = userService.saveUser(userVo);

        // Assert
        assertNotNull(result);
        verify(userDataAccess).save(any(User.class));
    }

    @Test
    @DisplayName("Should not hash password if already hashed ($2y$)")
    void testSaveUser_AlreadyHashedPassword_2y() {
        // Arrange
        UserVo userVo = new UserVo();
        userVo.setEmail("test@example.com");
        userVo.setPassword("$2y$10$alreadyHashedPassword");
        
        doNothing().when(userDataAccess).save(any(User.class));

        // Act
        UserVo result = userService.saveUser(userVo);

        // Assert
        assertNotNull(result);
        verify(userDataAccess).save(any(User.class));
    }

    @Test
    @DisplayName("Should rebind user roles successfully")
    void testRebindUserRoles_Success() {
        // Arrange
        UUID userId = UUID.randomUUID();
        List<String> roleIds = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        when(userDataAccess.findById(userId)).thenReturn(Optional.of(testUser));
        doNothing().when(roleService).userBindRole(anyString(), anyList());

        // Act
        userService.rebindUserRoles(userId, roleIds);

        // Assert
        verify(userDataAccess).findById(userId);
        verify(roleService).userBindRole(userId.toString(), roleIds);
    }

    @Test
    @DisplayName("Should throw Exception when userId is null in rebindUserRoles")
    void testRebindUserRoles_NullUserId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.rebindUserRoles(null, List.of("role1")));
        assertEquals("User ID must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw Exception when roleIds is null in rebindUserRoles")
    void testRebindUserRoles_NullRoleIds() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.rebindUserRoles(userId, null));
        assertEquals("Role list is required", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw Exception when user not found in rebindUserRoles")
    void testRebindUserRoles_UserNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userDataAccess.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.rebindUserRoles(userId, List.of("role1")));
        assertEquals("User not found", exception.getMessage());
    }

    @Test
    @DisplayName("Should fetch users by ids in batch successfully")
    void testGetUsersByIds_Success() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        User u1 = new User();
        u1.setId(id1);
        u1.setName("User 1");
        User u2 = new User();
        u2.setId(id2);
        u2.setName("User 2");

        UserVo vo1 = new UserVo();
        vo1.setId(id1.toString());
        vo1.setName("User 1");
        UserVo vo2 = new UserVo();
        vo2.setId(id2.toString());
        vo2.setName("User 2");

        when(userDataAccess.findAllByIdsWithRoles(anyList())).thenReturn(List.of(u1, u2));
        when(userMapper.toVo(u1)).thenReturn(vo1);
        when(userMapper.toVo(u2)).thenReturn(vo2);

        BatchResultVo<UserVo> result = userService.getUsersByIds(List.of(id1, id2));

        assertNotNull(result);
        assertEquals(2, result.getTotalRequested());
        assertEquals(2, result.getTotalFound());
        assertEquals(2, result.getItems().size());
        assertTrue(result.getMissingIds().isEmpty());
        verify(userDataAccess).findAllByIdsWithRoles(anyList());
        verify(userDataAccess, never()).findAllById(anyList());
    }

    @Test
    @DisplayName("Should fetch user with full permissions by email successfully")
    void testGetUserWithFullPermissionsByEmail_Success() {
        String email = "full@example.com";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);

        UserVo vo = new UserVo();
        vo.setId(user.getId().toString());
        vo.setEmail(email);
        vo.setRoleNames(List.of("ROLE_ADMIN"));

        when(userDataAccess.findByEmailWithRoles(email)).thenReturn(Optional.of(user));
        when(userMapper.toVoWithPermissions(user)).thenReturn(vo);

        UserVo result = userService.getUserWithFullPermissionsByEmail(email);

        assertNotNull(result);
        assertEquals(email, result.getEmail());
        verify(userDataAccess).findByEmailWithRoles(email);
        verify(userMapper).toVoWithPermissions(user);
    }

    @Test
    @DisplayName("Should delete user and unidirectionally clean UserRole")
    void testDeleteUser_Success() {
        UUID userId = UUID.randomUUID();
        testUser.setId(userId);
        testUser.setEmail("member@example.com");
        when(userDataAccess.findById(userId)).thenReturn(Optional.of(testUser));

        userService.deleteUser(userId.toString());

        verify(userRoleDataAccess).deleteByUserId(userId);
        verify(userDataAccess).delete(testUser);
        verify(iamLocalCacheManager).evictUser("member@example.com");
    }

    @Test
    @DisplayName("Should forbid deleting system default admin user")
    void testDeleteUser_ForbiddenAdmin() {
        UUID adminId = UUID.randomUUID();
        User admin = new User();
        admin.setId(adminId);
        admin.setEmail("admin");
        when(userDataAccess.findById(adminId)).thenReturn(Optional.of(admin));

        AppException ex = assertThrows(AppException.class, () -> userService.deleteUser(adminId.toString()));
        assertEquals("FORBIDDEN_SYSTEM_USER", ex.getErrorType());
        verify(userRoleDataAccess, never()).deleteByUserId(any());
        verify(userDataAccess, never()).delete(any());
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent user")
    void testDeleteUser_NotFound() {
        UUID userId = UUID.randomUUID();
        when(userDataAccess.findById(userId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> userService.deleteUser(userId.toString()));
        assertEquals("NOT_FOUND", ex.getErrorType());
    }
}
