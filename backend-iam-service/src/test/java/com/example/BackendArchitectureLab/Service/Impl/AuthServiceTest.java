package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserDataAccess;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Mapper.UserMapper;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Vo.SignupRequest;
import com.example.BackendArchitectureLab.Vo.SuperUserRequest;
import com.example.BackendArchitectureLab.Vo.UserVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 * Covers authentication composition logic: current user info, parent permissions,
 * signup and super user creation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private IUserDataAccess userDataAccess;
    @Mock
    private IFunctionDataAccess functionDataAccess;
    @Mock
    private IUserService userService;
    @Mock
    private IRoleService roleService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private FunctionMapper functionMapper;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "superUserKey", "test-super-key");

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setPassword("hashedPassword");
        testUser.setDisabled(false);
    }

    private void stubUserMapperToVo() {
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

        when(functionDataAccess.findAllById(List.of(childId)))
                .thenReturn(List.of(childFunction));
        when(functionDataAccess.findAllById(List.of(parentId)))
                .thenReturn(List.of(parentFunction));
        when(functionDataAccess.findAllById(argThat(list ->
                list.contains(parentId) && list.contains(grandParentId)
        ))).thenReturn(List.of(grandParentFunction));

        // Act
        List<FunctionVo> result = authService.getAllParent(List.of(childId.toString()));

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
        List<FunctionVo> result = authService.getAllParent(new ArrayList<>());

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
        childFunction.setParent("");

        when(functionDataAccess.findAllById(List.of(childId)))
                .thenReturn(List.of(childFunction));
        when(functionDataAccess.findAllById(new ArrayList<>()))
                .thenReturn(new ArrayList<>());

        // Act
        List<FunctionVo> result = authService.getAllParent(List.of(childId.toString()));

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(functionDataAccess, times(3)).findAllById(anyList());
    }

    @Test
    @DisplayName("Should get current user info with parent permissions")
    void testGetCurrentUserInfo() {
        // Arrange
        UUID functionId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test@example.com", null));
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
        UserVo result = authService.getCurrentUserInfo();

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
                new UsernamePasswordAuthenticationToken("notfound@example.com", null));
        when(userDataAccess.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> authService.getCurrentUserInfo());
        assertEquals("User not found", exception.getMessage());
    }

    @Test
    @DisplayName("Should signup user and bind default user role")
    void testSignup_Success() {
        // Arrange
        SignupRequest request = new SignupRequest();
        request.setEmail("new@example.com");
        request.setPassword("password");

        when(userService.getUserByEmail("new@example.com")).thenReturn(List.of());

        UserVo savedUser = new UserVo();
        savedUser.setId(UUID.randomUUID().toString());
        savedUser.setEmail("new@example.com");
        when(userService.createUser(any(UserVo.class))).thenReturn(savedUser);

        RoleOutVo userRole = new RoleOutVo();
        userRole.setId(UUID.randomUUID());
        userRole.setName("user");
        when(roleService.getRole()).thenReturn(List.of(userRole));
        doNothing().when(roleService).userBindRole(anyString(), anyList());

        // Act
        UserVo result = authService.signup(request);

        // Assert
        assertNotNull(result);
        assertEquals("new@example.com", result.getEmail());
        verify(userService).createUser(any(UserVo.class));
        verify(roleService).userBindRole(eq(savedUser.getId()), anyList());
    }

    @Test
    @DisplayName("Should throw Exception when email already exists in signup")
    void testSignup_DuplicateEmail() {
        // Arrange
        SignupRequest request = new SignupRequest();
        request.setEmail("existing@example.com");
        request.setPassword("password");

        UserVo existing = new UserVo();
        existing.setEmail("existing@example.com");
        when(userService.getUserByEmail("existing@example.com")).thenReturn(List.of(existing));

        // Act & Assert
        AppException exception = assertThrows(AppException.class,
                () -> authService.signup(request));
        assertEquals("User already exists", exception.getMessage());
        verify(userService, never()).createUser(any(UserVo.class));
    }

    @Test
    @DisplayName("Should signup without role binding when no default role exists")
    void testSignup_NoDefaultRole() {
        // Arrange
        SignupRequest request = new SignupRequest();
        request.setEmail("new@example.com");
        request.setPassword("password");

        when(userService.getUserByEmail("new@example.com")).thenReturn(List.of());
        when(userService.createUser(any(UserVo.class))).thenAnswer(invocation -> {
            UserVo vo = invocation.getArgument(0);
            vo.setId(UUID.randomUUID().toString());
            return vo;
        });
        when(roleService.getRole()).thenReturn(List.of());

        // Act
        UserVo result = authService.signup(request);

        // Assert
        assertNotNull(result);
        verify(roleService, never()).userBindRole(anyString(), anyList());
    }

    @Test
    @DisplayName("Should create super user with existing admin role")
    void testCreateSuperUser_Success() {
        // Arrange
        SuperUserRequest request = new SuperUserRequest();
        request.setKey("test-super-key");
        request.setEmail("admin@example.com");

        when(userService.getUserByEmail("admin@example.com")).thenReturn(List.of());
        when(userService.createUser(any(UserVo.class))).thenAnswer(invocation -> {
            UserVo vo = invocation.getArgument(0);
            vo.setId(UUID.randomUUID().toString());
            return vo;
        });

        RoleOutVo adminRole = new RoleOutVo();
        adminRole.setId(UUID.randomUUID());
        adminRole.setName("admin");
        when(roleService.getRoleByName("admin")).thenReturn(adminRole);
        doNothing().when(roleService).userBindRole(anyString(), anyList());

        // Act
        UserVo result = authService.createSuperUser(request);

        // Assert
        assertNotNull(result);
        assertEquals("admin@example.com", result.getEmail());
        verify(roleService, never()).addRole(any());
        verify(roleService).userBindRole(eq(result.getId()), anyList());
    }

    @Test
    @DisplayName("Should create admin role when not exists in createSuperUser")
    void testCreateSuperUser_AdminRoleNotExists() {
        // Arrange
        SuperUserRequest request = new SuperUserRequest();
        request.setKey("test-super-key");
        request.setEmail("admin@example.com");

        when(userService.getUserByEmail("admin@example.com")).thenReturn(List.of());
        when(userService.createUser(any(UserVo.class))).thenAnswer(invocation -> {
            UserVo vo = invocation.getArgument(0);
            vo.setId(UUID.randomUUID().toString());
            return vo;
        });

        when(roleService.getRoleByName("admin")).thenReturn(null);
        RoleOutVo adminRole = new RoleOutVo();
        adminRole.setId(UUID.randomUUID());
        adminRole.setName("admin");
        when(roleService.addRole(any(RoleOutVo.class))).thenReturn(adminRole);
        doNothing().when(roleService).userBindRole(anyString(), anyList());

        // Act
        UserVo result = authService.createSuperUser(request);

        // Assert
        assertNotNull(result);
        verify(roleService).addRole(any(RoleOutVo.class));
        verify(roleService).userBindRole(eq(result.getId()), anyList());
    }

    @Test
    @DisplayName("Should use default email admin when email is blank in createSuperUser")
    void testCreateSuperUser_DefaultEmail() {
        // Arrange
        SuperUserRequest request = new SuperUserRequest();
        request.setKey("test-super-key");
        request.setEmail("  ");

        when(userService.getUserByEmail("admin")).thenReturn(List.of());
        when(userService.createUser(any(UserVo.class))).thenAnswer(invocation -> {
            UserVo vo = invocation.getArgument(0);
            vo.setId(UUID.randomUUID().toString());
            return vo;
        });

        RoleOutVo adminRole = new RoleOutVo();
        adminRole.setId(UUID.randomUUID());
        adminRole.setName("admin");
        when(roleService.getRoleByName("admin")).thenReturn(adminRole);
        doNothing().when(roleService).userBindRole(anyString(), anyList());

        // Act
        UserVo result = authService.createSuperUser(request);

        // Assert
        assertNotNull(result);
        assertEquals("admin", result.getEmail());
    }

    @Test
    @DisplayName("Should throw Exception when superuser key is invalid")
    void testCreateSuperUser_InvalidKey() {
        // Arrange
        SuperUserRequest request = new SuperUserRequest();
        request.setKey("wrong-key");
        request.setEmail("admin@example.com");

        // Act & Assert
        AppException exception = assertThrows(AppException.class,
                () -> authService.createSuperUser(request));
        assertEquals("Invalid key", exception.getMessage());
        verify(userService, never()).createUser(any(UserVo.class));
    }

    @Test
    @DisplayName("Should throw Exception when admin email already exists")
    void testCreateSuperUser_DuplicateEmail() {
        // Arrange
        SuperUserRequest request = new SuperUserRequest();
        request.setKey("test-super-key");
        request.setEmail("admin@example.com");

        UserVo existing = new UserVo();
        existing.setEmail("admin@example.com");
        when(userService.getUserByEmail("admin@example.com")).thenReturn(List.of(existing));

        // Act & Assert
        AppException exception = assertThrows(AppException.class,
                () -> authService.createSuperUser(request));
        assertEquals("User already exists", exception.getMessage());
        verify(userService, never()).createUser(any(UserVo.class));
    }
}
