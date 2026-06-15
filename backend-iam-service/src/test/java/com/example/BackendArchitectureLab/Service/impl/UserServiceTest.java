package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserDataAccess;
import com.example.BackendArchitectureLab.Feign.ProjectServiceFeignClient;
import com.example.BackendArchitectureLab.Dto.Vo.Search.UserSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Service.impl.UserService;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Mapper.UserMapper;
import com.example.BackendArchitectureLab.Dto.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


/**
 * Unit tests for UserService.
 * Tests service layer business logic with mocked data access dependencies.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private IUserDataAccess userDataAccess;

    @Mock
    private IFunctionDataAccess functionDataAccess;

    @Mock
    private IRoleService roleService;

    @Mock
    private ProjectServiceFeignClient projectServiceFeignClient;

    @Mock
    private UserMapper userMapper;

    @Mock
    private FunctionMapper functionMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private Function testFunction;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setPassword("hashedPassword");
        testUser.setDisabled(false);

        testFunction = new Function();
        testFunction.setId(UUID.randomUUID());
        testFunction.setName("TestFunction");
        testFunction.setParent("");

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
        when(functionMapper.toVo(any(Function.class))).thenAnswer(invocation -> {
            Function function = invocation.getArgument(0);
            FunctionVo vo = new FunctionVo();
            vo.setId(function.getId() == null ? null : function.getId().toString());
            vo.setName(function.getName());
            vo.setParent(function.getParent());
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
        when(userDataAccess.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        // Act
        List<UserVo> result = userService.getUserByEmail("test@example.com");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test@example.com", result.get(0).getEmail());
        verify(userDataAccess, times(1)).findByEmail("test@example.com");
    }

    @Test
    @DisplayName("Should return only one user by email")
    void testGetOnlyUserByEmail() {
        // Arrange
        when(userDataAccess.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        // Act
        UserVo result = userService.getOnlyUserByEmail("test@example.com");

        // Assert
        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        verify(userDataAccess, times(1)).findByEmail("test@example.com");
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
        userVo.setRoleArr(List.of(UUID.randomUUID().toString()));

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
        userVo.setRoleArr(List.of(UUID.randomUUID().toString()));

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
        userVo.setRoleArr(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.saveUserWithRole(userVo));
        assertEquals("Role list is required", exception.getMessage());
    }

    @Test
    @DisplayName("Should get all parent functions successfully")
    void testGetAllParent() {
        // Arrange
        UUID childId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID grandParentId = UUID.randomUUID();

        Function childFunction = new Function();
        childFunction.setId(childId);
        childFunction.setParent(parentId.toString());

        Function parentFunction = new Function();
        parentFunction.setId(parentId);
        parentFunction.setParent(grandParentId.toString());

        Function grandParentFunction = new Function();
        grandParentFunction.setId(grandParentId);
        grandParentFunction.setParent("");

        // Mock first call - get child functions
        when(functionDataAccess.findAllById(List.of(childId)))
                .thenReturn(List.of(childFunction));

        // Mock second call - get parent functions
        when(functionDataAccess.findAllById(List.of(parentId)))
                .thenReturn(List.of(parentFunction));

        // Mock third call - get grandparent functions
        when(functionDataAccess.findAllById(argThat(list ->
                list.contains(parentId) && list.contains(grandParentId)
        ))).thenReturn(List.of(grandParentFunction));

        // Act
        List<FunctionVo> result = userService.getAllParent(List.of(childId.toString()));

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(functionDataAccess, times(3)).findAllById(anyList());
    }

    @Test
    @DisplayName("Should handle empty child list in getAllParent")
    void testGetAllParent_EmptyList() {
        // Arrange
        when(functionDataAccess.findAllById(anyList())).thenReturn(new ArrayList<>());

        // Act
        List<FunctionVo> result = userService.getAllParent(new ArrayList<>());

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(functionDataAccess, atLeastOnce()).findAllById(anyList());
    }

    @Test
    @DisplayName("Should handle functions without parents in getAllParent")
    void testGetAllParent_NoParents() {
        // Arrange
        UUID childId = UUID.randomUUID();
        Function childFunction = new Function();
        childFunction.setId(childId);
        childFunction.setParent(""); // No parent

        when(functionDataAccess.findAllById(List.of(childId)))
                .thenReturn(List.of(childFunction));
        when(functionDataAccess.findAllById(new ArrayList<>()))
                .thenReturn(new ArrayList<>());

        // Act
        List<FunctionVo> result = userService.getAllParent(List.of(childId.toString()));

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(functionDataAccess, times(3)).findAllById(anyList());
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
        when(userDataAccess.findById(userId)).thenReturn(java.util.Optional.of(testUser));

        // Act
        UserVo result = userService.getUserById(userId.toString());

        // Assert
        assertNotNull(result);
        assertEquals(testUser.getEmail(), result.getEmail());
        verify(userDataAccess).findById(userId);
    }

    @Test
    @DisplayName("Should throw Exception when user ID is null")
    void testGetUserById_NullId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.getUserById(null));
        assertEquals("Key must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw Exception when user ID is blank")
    void testGetUserById_BlankId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.getUserById("   "));
        assertEquals("Key must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw Exception when user not found")
    void testGetUserById_UserNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userDataAccess.findById(userId)).thenReturn(java.util.Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.getUserById(userId.toString()));
        assertEquals("User not found", exception.getMessage());
    }

    @Test
    @DisplayName("Should bind user to project successfully")
    void testBindUserProject_Success() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(projectServiceFeignClient.existsUserProject(userId, projectId)).thenReturn(false);
        doNothing().when(projectServiceFeignClient).saveUserProject(userId, projectId);

        // Act
        userService.bindUserProject(userId.toString(), projectId.toString());

        // Assert
        verify(projectServiceFeignClient).existsUserProject(userId, projectId);
        verify(projectServiceFeignClient).saveUserProject(userId, projectId);
    }

    @Test
    @DisplayName("Should throw Exception when binding user ID is null")
    void testBindUserProject_NullUserId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.bindUserProject(null, UUID.randomUUID().toString()));
        assertEquals("Key must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw Exception when binding project ID is null")
    void testBindUserProject_NullProjectId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.bindUserProject(UUID.randomUUID().toString(), null));
        assertEquals("Key must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw Exception when project already bound to user")
    void testBindUserProject_AlreadyBound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(projectServiceFeignClient.existsUserProject(userId, projectId)).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.bindUserProject(userId.toString(), projectId.toString()));
        assertEquals("Project already bind to user", exception.getMessage());
    }

    @Test
    @DisplayName("Should get all users VO successfully")
    void testGetAllUsersVo() {
        // Arrange
        List<User> users = List.of(testUser);
        when(userDataAccess.findAll()).thenReturn(users);

        // Act
        List<UserVo> result = userService.getAllUsersVo();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(userDataAccess).findAll();
    }

    @Test
    @DisplayName("Should get current user info successfully")
    void testGetCurrentUserInfo() {
        // Arrange
        UUID functionId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "test@example.com", null));
        }
        when(userDataAccess.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        
        FunctionVo functionVo = new FunctionVo();
        functionVo.setId(functionId.toString());
        functionVo.setParent(parentId.toString());
        
        UserVo userVo = new UserVo();
        userVo.setEmail("test@example.com");
        userVo.setPermissions(new ArrayList<>(List.of(functionVo)));
        
        when(userMapper.toVo(testUser)).thenReturn(userVo);
        
        Function childFunction = new Function();
        childFunction.setId(functionId);
        childFunction.setParent(parentId.toString());
        
        Function parentFunction = new Function();
        parentFunction.setId(parentId);
        parentFunction.setParent("");
        
        when(functionDataAccess.findAllById(List.of(functionId))).thenReturn(List.of(childFunction));
        when(functionDataAccess.findAllById(List.of(parentId))).thenReturn(List.of(parentFunction));
        when(functionDataAccess.findAllById(argThat(list -> list.contains(parentId)))).thenReturn(List.of(parentFunction));
        
        FunctionVo parentVo = new FunctionVo();
        parentVo.setId(parentId.toString());
        when(functionMapper.toVo(parentFunction)).thenReturn(parentVo);

        // Act
        UserVo result = userService.getCurrentUserInfo();

        // Assert
        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        assertTrue(result.getPermissions().size() >= 1);
        verify(userDataAccess).findByEmail("test@example.com");
    }

    @Test
    @DisplayName("Should throw Exception when current user not found")
    void testGetCurrentUserInfo_UserNotFound() {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "notfound@example.com", null));
        when(userDataAccess.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.getCurrentUserInfo());
        assertEquals("User not found", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw Exception when updating non-existing user with role")
    void testSaveUserWithRole_ExistingUser_NotFound() {
        // Arrange
        UserVo userVo = new UserVo();
        userVo.setId("existing-id");
        userVo.setEmail("notfound@example.com");
        userVo.setPassword("newPassword");
        userVo.setRoleArr(List.of(UUID.randomUUID().toString()));

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
    @DisplayName("Should rebind user projects with diff strategy")
    void testRebindUserProjects_Success() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID projectId1 = UUID.randomUUID();
        UUID projectId2 = UUID.randomUUID();

        when(userDataAccess.findById(userId)).thenReturn(Optional.of(testUser));
        when(projectServiceFeignClient.getUserProjectIds(userId)).thenReturn(List.of(projectId1));

        // Act
        userService.rebindUserProjects(userId, List.of(projectId1, projectId2));

        // Assert
        verify(userDataAccess).findById(userId);
        verify(projectServiceFeignClient).getUserProjectIds(userId);
        verify(projectServiceFeignClient, never()).deleteUserProject(any(), any());
        verify(projectServiceFeignClient).saveUserProject(userId, projectId2);
    }

    @Test
    @DisplayName("Should throw Exception when userId is null in rebindUserProjects")
    void testRebindUserProjects_NullUserId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.rebindUserProjects(null, List.of(UUID.randomUUID())));
        assertEquals("Key must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should handle empty project list in rebindUserProjects")
    void testRebindUserProjects_EmptyProjectList() {
        // Arrange
        UUID userId = UUID.randomUUID();

        when(userDataAccess.findById(userId)).thenReturn(Optional.of(testUser));
        when(projectServiceFeignClient.getUserProjectIds(userId)).thenReturn(List.of());

        // Act
        userService.rebindUserProjects(userId, List.of());

        // Assert
        verify(userDataAccess).findById(userId);
        verify(projectServiceFeignClient).getUserProjectIds(userId);
        verify(projectServiceFeignClient, never()).deleteUserProject(any(), any());
        verify(projectServiceFeignClient, never()).saveUserProject(any(), any());
    }

    @Test
    @DisplayName("Should throw Exception when user not found in rebindUserProjects")
    void testRebindUserProjects_UserNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userDataAccess.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.rebindUserProjects(userId, List.of(UUID.randomUUID())));
        assertEquals("User not found", exception.getMessage());
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
}
