package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Search.RoleSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Entity.*;
import com.example.BackendArchitectureLab.Service.impl.RoleService;
import com.example.BackendArchitectureLab.DataAccess.*;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Mapper.RoleMapper;
import com.example.BackendArchitectureLab.Mapper.UserMapper;
import com.example.BackendArchitectureLab.Dto.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Dto.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RoleService.
 * Tests service layer business logic with mocked data access dependencies.
 * Target: 85%+ coverage
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleServiceTest {

    @Mock
    private IRoleDataAccess roleDataAccess;

    @Mock
    private IRoleFunctionDataAccess roleFunctionDataAccess;

    @Mock
    private IFunctionDataAccess functionDataAccess;

    @Mock
    private IUserDataAccess userDataAccess;

    @Mock
    private IUserRoleDataAccess userRoleDataAccess;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private FunctionMapper functionMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private RoleService roleService;

    private Role testRole;
    private Function testFunction;
    private User testUser;
    private UUID testRoleKey;
    private UUID testFunctionId;
    private UUID testUserKey;

    @BeforeEach
    void setUp() {
        // Setup test role
        testRoleKey = UUID.randomUUID();
        testRole = new Role();
        testRole.setId(testRoleKey);
        testRole.setName("ROLE_ADMIN");
        testRole.setDescription("Admin role");

        // Setup test function
        testFunctionId = UUID.randomUUID();
        testFunction = new Function();
        testFunction.setId(testFunctionId);
        testFunction.setName("TestFunction");
        testFunction.setParent("");

        // Setup test user
        testUserKey = UUID.randomUUID();
        testUser = new User();
        testUser.setId(testUserKey);
        testUser.setEmail("user@example.com");

        when(roleMapper.toEntity(any(RoleOutVo.class))).thenAnswer(invocation -> {
            RoleOutVo vo = invocation.getArgument(0);
            Role role = new Role();
            role.setId(vo.getId());
            role.setName(vo.getName());
            role.setDescription(vo.getDescription());
            return role;
        });
        when(roleMapper.toVo(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            RoleOutVo vo = new RoleOutVo();
            vo.setId(role.getId());
            vo.setName(role.getName());
            vo.setDescription(role.getDescription());
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
        when(userMapper.toVo(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            UserVo vo = new UserVo();
            vo.setId(user.getId() == null ? null : user.getId().toString());
            vo.setEmail(user.getEmail());
            return vo;
        });
    }

    // ==================== Group 1: CRUD Operations ====================

    @Test
    @DisplayName("Should add role successfully")
    void testAddRole_Success() {
        // Arrange
        RoleOutVo newRole = new RoleOutVo();
        newRole.setName("ROLE_USER");
        Role savedRole = new Role();
        savedRole.setName("ROLE_USER");
        
        when(roleDataAccess.exists(any(Example.class))).thenReturn(false);
        when(roleDataAccess.save(any(Role.class))).thenReturn(savedRole);

        // Act
        RoleOutVo result = roleService.addRole(newRole);

        // Assert
        assertNotNull(result);
        verify(roleDataAccess, times(1)).exists(any(Example.class));
        verify(roleDataAccess, times(1)).save(any(Role.class));
    }

    @Test
    @DisplayName("Should add role and bind functions")
    void testAddRoleWithFunctions() {
        RoleOutVo newRole = new RoleOutVo();
        newRole.setName("ROLE_USER");
        newRole.setFunctionIds(List.of(testFunctionId.toString()));

        Role savedRole = new Role();
        savedRole.setId(testRoleKey);
        savedRole.setName("ROLE_USER");

        when(roleDataAccess.exists(any(Example.class))).thenReturn(false);
        when(roleDataAccess.save(any(Role.class))).thenReturn(savedRole);
        when(roleDataAccess.findById(testRoleKey)).thenReturn(Optional.of(savedRole));
        when(functionDataAccess.findAllById(List.of(testFunctionId))).thenReturn(List.of(testFunction));
        doNothing().when(roleFunctionDataAccess).deleteByRoleKey(testRoleKey);
        when(roleFunctionDataAccess.saveAll(anyList())).thenReturn(List.of());

        RoleOutVo result = roleService.addRoleWithFunctions(newRole);

        assertNotNull(result);
        verify(roleFunctionDataAccess).deleteByRoleKey(testRoleKey);
        verify(functionDataAccess).findAllById(List.of(testFunctionId));
        verify(roleFunctionDataAccess).saveAll(anyList());
    }

    @Test
    @DisplayName("Should throw Exception when adding role with non-null key")
    void testAddRole_KeyNotNull() {
        // Arrange
        RoleOutVo newRole = new RoleOutVo();
        newRole.setId(UUID.randomUUID());
        newRole.setName("ROLE_USER");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> roleService.addRole(newRole));
        assertEquals("Key must be null", exception.getMessage());
        verify(roleDataAccess, never()).save(any());
    }

    @Test
    @DisplayName("Should throw Exception when adding role with null name")
    void testAddRole_NameIsNull() {
        // Arrange
        RoleOutVo newRole = new RoleOutVo();
        newRole.setName(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> roleService.addRole(newRole));
        assertEquals("Name must not be null", exception.getMessage());
        verify(roleDataAccess, never()).save(any());
    }

    @Test
    @DisplayName("Should throw Exception when adding role with duplicate name")
    void testAddRole_NameAlreadyExists() {
        // Arrange
        RoleOutVo newRole = new RoleOutVo();
        newRole.setName("ROLE_USER");
        
        when(roleDataAccess.exists(any(Example.class))).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> roleService.addRole(newRole));
        assertEquals("Name already exists", exception.getMessage());
        verify(roleDataAccess, never()).save(any());
    }

    @Test
    @DisplayName("Should get all roles as RoleOutVo")
    void testGetRole() {
        // Arrange
        List<Role> roles = Arrays.asList(testRole);
        when(roleDataAccess.findAll()).thenReturn(roles);

        // Act
        List<RoleOutVo> result = roleService.getRole();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(roleDataAccess, times(1)).findAll();
    }

    @Test
    @DisplayName("Should get role by id")
    void testGetRoleById() {
        // Arrange
        when(roleDataAccess.findById(testRoleKey)).thenReturn(Optional.of(testRole));

        // Act
        RoleOutVo result = roleService.getRoleById(testRoleKey.toString());

        // Assert
        assertNotNull(result);
        assertEquals(testRole.getName(), result.getName());
        verify(roleDataAccess, times(1)).findById(testRoleKey);
    }

    @Test
    @DisplayName("Should update role successfully")
    void testUpdateRole() {
        // Arrange
        Role updateRoleEntity = new Role();
        updateRoleEntity.setId(testRoleKey);
        updateRoleEntity.setName("ROLE_ADMIN");
        updateRoleEntity.setDescription("Updated description");
        RoleOutVo updateRole = new RoleOutVo();
        updateRole.setId(testRoleKey);
        updateRole.setName("ROLE_ADMIN");
        updateRole.setDescription("Updated description");
        
        when(roleDataAccess.findById(testRoleKey)).thenReturn(Optional.of(testRole));
        when(roleDataAccess.save(any(Role.class))).thenReturn(updateRoleEntity);

        // Act
        RoleOutVo result = roleService.updateRole(updateRole);

        // Assert
        verify(roleDataAccess, times(1)).findById(testRoleKey);
        verify(roleDataAccess, times(1)).save(any(Role.class));
        assertEquals("Updated description", result.getDescription());
    }

    @Test
    @DisplayName("Should update role and replace functions")
    void testUpdateRoleWithFunctions() {
        RoleOutVo updateRole = new RoleOutVo();
        updateRole.setId(testRoleKey);
        updateRole.setName("ROLE_ADMIN");
        updateRole.setDescription("Updated description");
        updateRole.setFunctionIds(List.of(testFunctionId.toString()));

        when(roleDataAccess.findById(testRoleKey)).thenReturn(Optional.of(testRole));
        when(roleDataAccess.save(any(Role.class))).thenReturn(testRole);
        when(functionDataAccess.findAllById(List.of(testFunctionId))).thenReturn(List.of(testFunction));
        doNothing().when(roleFunctionDataAccess).deleteByRoleKey(testRoleKey);
        when(roleFunctionDataAccess.saveAll(anyList())).thenReturn(List.of());

        RoleOutVo result = roleService.updateRoleWithFunctions(updateRole);

        assertNotNull(result);
        verify(roleFunctionDataAccess).deleteByRoleKey(testRoleKey);
        verify(functionDataAccess).findAllById(List.of(testFunctionId));
        verify(roleFunctionDataAccess).saveAll(anyList());
    }

    @Test
    @DisplayName("Should keep role functions when functionIds is null")
    void testUpdateRoleWithFunctions_NullFunctionIdsKeepsBindings() {
        RoleOutVo updateRole = new RoleOutVo();
        updateRole.setId(testRoleKey);
        updateRole.setName("ROLE_ADMIN");
        updateRole.setFunctionIds(null);

        when(roleDataAccess.findById(testRoleKey)).thenReturn(Optional.of(testRole));
        when(roleDataAccess.save(any(Role.class))).thenReturn(testRole);

        roleService.updateRoleWithFunctions(updateRole);

        verify(roleFunctionDataAccess, never()).deleteByRoleKey(any());
        verify(functionDataAccess, never()).findAllById(anyList());
        verify(roleFunctionDataAccess, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Should clear role functions when functionIds is empty")
    void testUpdateRoleWithFunctions_EmptyFunctionIdsClearsBindings() {
        RoleOutVo updateRole = new RoleOutVo();
        updateRole.setId(testRoleKey);
        updateRole.setName("ROLE_ADMIN");
        updateRole.setFunctionIds(List.of());

        when(roleDataAccess.findById(testRoleKey)).thenReturn(Optional.of(testRole));
        when(roleDataAccess.save(any(Role.class))).thenReturn(testRole);
        doNothing().when(roleFunctionDataAccess).deleteByRoleKey(testRoleKey);

        roleService.updateRoleWithFunctions(updateRole);

        verify(roleFunctionDataAccess).deleteByRoleKey(testRoleKey);
        verify(functionDataAccess, never()).findAllById(anyList());
        verify(roleFunctionDataAccess, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Should delete role and its associated functions")
    void testDeleteRole() {
        // Arrange
        RoleOutVo roleToDelete = new RoleOutVo();
        roleToDelete.setId(testRoleKey);
        
        when(roleDataAccess.findById(testRoleKey)).thenReturn(Optional.of(testRole));
        when(roleFunctionDataAccess.findAll(any(Example.class))).thenReturn(new ArrayList<>());
        doNothing().when(roleFunctionDataAccess).deleteAll(anyList());
        doNothing().when(roleDataAccess).delete(any(Role.class));

        // Act
        roleService.deleteRole(roleToDelete);

        // Assert
        verify(roleDataAccess, times(1)).findById(testRoleKey);
        verify(roleFunctionDataAccess, times(1)).findAll(any(Example.class));
        verify(roleDataAccess, times(1)).delete(testRole);
    }

    // ==================== Group 2: Role-Function Binding Operations ====================

    @Test
    @DisplayName("Should bind functions to role")
    void testRoleBindFunction() {
        // Arrange
        when(roleDataAccess.findById(testRoleKey)).thenReturn(Optional.of(testRole));
        when(functionDataAccess.findAllById(anyList())).thenReturn(List.of(testFunction));
        doNothing().when(roleFunctionDataAccess).deleteByFunctionAndRole(anyList(), anyList());
        when(roleFunctionDataAccess.saveAll(anyList())).thenReturn(new ArrayList<>());

        // Act
        roleService.roleBindFunction(testRoleKey.toString(), List.of(testFunctionId.toString()));

        // Assert
        verify(roleDataAccess, times(1)).findById(testRoleKey);
        verify(roleFunctionDataAccess, times(1)).deleteByFunctionAndRole(anyList(), anyList());
        verify(functionDataAccess, times(1)).findAllById(anyList());
        verify(roleFunctionDataAccess, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("Should bind roles to function")
    void testFunctionBindRole() {
        // Arrange
        when(functionDataAccess.findById(testFunctionId)).thenReturn(Optional.of(testFunction));
        when(roleDataAccess.findAllById(anyList())).thenReturn(List.of(testRole));
        doNothing().when(roleFunctionDataAccess).deleteByFunctionAndRole(anyList(), anyList());
        when(roleFunctionDataAccess.saveAll(anyList())).thenReturn(new ArrayList<>());

        // Act
        roleService.functionBindRole(testFunctionId.toString(), List.of(testRoleKey.toString()));

        // Assert
        verify(roleFunctionDataAccess, times(1)).deleteByFunctionAndRole(anyList(), anyList());
        verify(roleFunctionDataAccess, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("Should unbind functions from role")
    void testRoleUnbindFunction() {
        // Arrange
        when(roleDataAccess.findById(testRoleKey)).thenReturn(Optional.of(testRole));
        when(functionDataAccess.findAllById(anyList())).thenReturn(List.of(testFunction));
        doNothing().when(roleFunctionDataAccess).deleteByFunctionAndRole(anyList(), anyList());

        // Act
        roleService.roleUnbindFunction(testRoleKey.toString(), List.of(testFunctionId.toString()));

        // Assert
        verify(roleFunctionDataAccess, times(1)).deleteByFunctionAndRole(anyList(), anyList());
    }

    @Test
    @DisplayName("Should unbind roles from function")
    void testFunctionUnbindRole() {
        // Arrange
        when(functionDataAccess.findById(testFunctionId)).thenReturn(Optional.of(testFunction));
        when(roleDataAccess.findAllById(anyList())).thenReturn(List.of(testRole));
        doNothing().when(roleFunctionDataAccess).deleteByFunctionAndRole(anyList(), anyList());

        // Act
        roleService.functionUnbindRole(testFunctionId.toString(), List.of(testRoleKey.toString()));

        // Assert
        verify(roleFunctionDataAccess, times(1)).deleteByFunctionAndRole(anyList(), anyList());
    }

    // ==================== Group 3: Role-User Binding Operations ====================

    @Test
    @DisplayName("Should bind users to role")
    void testRoleBindingUser() {
        // Arrange
        when(roleDataAccess.findById(testRoleKey)).thenReturn(Optional.of(testRole));
        when(userDataAccess.findAllById(anyList())).thenReturn(List.of(testUser));
        when(userRoleDataAccess.findByUserId(testUserKey)).thenReturn(List.of());
        doNothing().when(userRoleDataAccess).deleteByUserIdAndRoleId(any(), any());
        when(userRoleDataAccess.saveAll(anyList())).thenReturn(new ArrayList<>());

        // Act
        roleService.roleBindUser(testRoleKey.toString(), List.of(testUserKey.toString()));

        // Assert
        verify(userRoleDataAccess, never()).deleteByUserIdAndRoleId(any(), any());
        verify(userRoleDataAccess, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("Should bind roles to user")
    void testUserBindRole() {
        // Arrange
        when(userDataAccess.findById(testUserKey)).thenReturn(Optional.of(testUser));
        when(userRoleDataAccess.findByUserId(testUserKey)).thenReturn(List.of());
        when(roleDataAccess.findAllById(anyList())).thenReturn(List.of(testRole));
        doNothing().when(userRoleDataAccess).deleteByUserIdAndRoleId(any(), any());
        when(userRoleDataAccess.saveAll(anyList())).thenReturn(new ArrayList<>());

        // Act
        roleService.userBindRole(testUserKey.toString(), List.of(testRoleKey.toString()));

        // Assert
        verify(userRoleDataAccess, never()).deleteByUserIdAndRoleId(any(), any());
        verify(userRoleDataAccess, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("Should update user roles by delta")
    void testUserBindRole_DeltaSync() {
        UUID oldRoleId = UUID.randomUUID();
        UUID newRoleId = UUID.randomUUID();

        Role oldRole = new Role();
        oldRole.setId(oldRoleId);
        Role newRole = new Role();
        newRole.setId(newRoleId);

        UserRole existing = new UserRole();
        existing.setUser(testUser);
        existing.setRole(oldRole);

        when(userDataAccess.findById(testUserKey)).thenReturn(Optional.of(testUser));
        when(userRoleDataAccess.findByUserId(testUserKey)).thenReturn(List.of(existing));
        when(roleDataAccess.findAllById(List.of(newRoleId))).thenReturn(List.of(newRole));
        doNothing().when(userRoleDataAccess).deleteByUserIdAndRoleId(testUserKey, oldRoleId);
        when(userRoleDataAccess.saveAll(anyList())).thenReturn(List.of());

        roleService.userBindRole(testUserKey.toString(), List.of(newRoleId.toString()));

        verify(userRoleDataAccess).deleteByUserIdAndRoleId(testUserKey, oldRoleId);
        verify(userRoleDataAccess).saveAll(anyList());
    }

    @Test
    @DisplayName("Should clear all roles when target role list is empty")
    void testUserBindRole_ClearAllWhenEmpty() {
        when(userDataAccess.findById(testUserKey)).thenReturn(Optional.of(testUser));
        doNothing().when(userRoleDataAccess).deleteByUserId(testUserKey);

        roleService.userBindRole(testUserKey.toString(), List.of());

        verify(userRoleDataAccess).deleteByUserId(testUserKey);
        verify(userRoleDataAccess, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Should reject null role list in userBindRole")
    void testUserBindRole_NullRoleList() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> roleService.userBindRole(testUserKey.toString(), null));
        assertEquals("Role list is required", exception.getMessage());
    }

    @Test
    @DisplayName("Should unbind users from role")
    void testRoleUnbindUser() {
        // Arrange
        when(roleDataAccess.findById(testRoleKey)).thenReturn(Optional.of(testRole));
        when(userDataAccess.findAllById(anyList())).thenReturn(List.of(testUser));
        doNothing().when(userRoleDataAccess).deleteAllByUserInAndRoleIn(anyList(), anyList());

        // Act
        roleService.roleUnbindUser(testRoleKey.toString(), List.of(testUserKey.toString()));

        // Assert
        verify(userRoleDataAccess, times(1)).deleteAllByUserInAndRoleIn(anyList(), anyList());
    }

    @Test
    @DisplayName("Should unbind roles from user")
    void testUserUnbindRole() {
        // Arrange
        when(userDataAccess.findById(testUserKey)).thenReturn(Optional.of(testUser));
        when(roleDataAccess.findAllById(anyList())).thenReturn(List.of(testRole));
        doNothing().when(userRoleDataAccess).deleteAllByUserInAndRoleIn(anyList(), anyList());

        // Act
        roleService.userUnbindRole(testUserKey.toString(), List.of(testRoleKey.toString()));

        // Assert
        verify(userRoleDataAccess, times(1)).deleteAllByUserInAndRoleIn(anyList(), anyList());
    }

    @Test
    @DisplayName("Should unbind all roles from user")
    void testUserUnbindAllRole() {
        // Arrange
        List<Role> allRoles = Arrays.asList(testRole);
        when(userDataAccess.findById(testUserKey)).thenReturn(Optional.of(testUser));
        when(roleDataAccess.findAll()).thenReturn(allRoles);
        doNothing().when(userRoleDataAccess).deleteAllByUserInAndRoleIn(anyList(), anyList());

        // Act
        roleService.userUnbindAllRole(testUserKey.toString());

        // Assert
        verify(roleDataAccess, times(1)).findAll();
        verify(userRoleDataAccess, times(1)).deleteAllByUserInAndRoleIn(List.of(testUser), allRoles);
    }

    // ==================== Group 4: Query Operations ====================

    @Test
    @DisplayName("Should get functions by role")
    void testGetFunctionByRole() {
        // Arrange
        RoleFunction roleFunction = new RoleFunction();
        roleFunction.setRole(testRole);
        roleFunction.setFunction(testFunction);
        
        List<RoleFunction> roleFunctions = Arrays.asList(roleFunction);
        testRole.setRoleFunctions(roleFunctions);
        
        when(roleDataAccess.findById(testRoleKey)).thenReturn(Optional.of(testRole));

        // Act
        List<FunctionVo> result = roleService.getFunctionByRole(testRoleKey.toString());

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testFunction.getName(), result.get(0).getName());
        verify(roleDataAccess, times(1)).findById(testRoleKey);
    }

    @Test
    @DisplayName("Should get roles by function")
    void testGetRoleByFunction() {
        // Arrange
        RoleFunction roleFunction = new RoleFunction();
        roleFunction.setRole(testRole);
        roleFunction.setFunction(testFunction);
        
        List<RoleFunction> roleFunctions = Arrays.asList(roleFunction);
        testFunction.setRoleFunctions(roleFunctions);
        
        when(functionDataAccess.findById(testFunctionId)).thenReturn(Optional.of(testFunction));

        // Act
        List<RoleOutVo> result = roleService.getRoleByFunction(testFunctionId.toString());

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testRole.getName(), result.get(0).getName());
        verify(functionDataAccess, times(1)).findById(testFunctionId);
    }

    @Test
    @DisplayName("Should get users by role")
    void testGetUserByRole() {
        // Arrange
        UserRole userRole = new UserRole();
        userRole.setRole(testRole);
        userRole.setUser(testUser);
        
        List<UserRole> userRoles = Arrays.asList(userRole);
        testRole.setUserRoles(userRoles);
        
        when(roleDataAccess.findById(testRoleKey)).thenReturn(Optional.of(testRole));

        // Act
        List<UserVo> result = roleService.getUserByRole(testRoleKey.toString());

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testUser.getEmail(), result.get(0).getEmail());
        verify(roleDataAccess, times(1)).findById(testRoleKey);
    }

    @Test
    @DisplayName("Should get roles by user")
    void testGetRoleByUser() {
        // Arrange
        UserRole userRole = new UserRole();
        userRole.setRole(testRole);
        userRole.setUser(testUser);
        
        List<UserRole> roles = Arrays.asList(userRole);
        testUser.setRoles(roles);
        
        when(userDataAccess.findById(testUserKey)).thenReturn(Optional.of(testUser));

        // Act
        List<RoleOutVo> result = roleService.getRoleByUser(testUserKey.toString());

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testRole.getName(), result.get(0).getName());
        verify(userDataAccess, times(1)).findById(testUserKey);
    }

    @Test
    @DisplayName("Should get role by name")
    void testGetRoleByName() {
        // Arrange
        when(roleDataAccess.findRoleByName("ROLE_ADMIN")).thenReturn(testRole);

        // Act
        RoleOutVo result = roleService.getRoleByName("ROLE_ADMIN");

        // Assert
        assertNotNull(result);
        assertEquals("ROLE_ADMIN", result.getName());
        verify(roleDataAccess, times(1)).findRoleByName("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should search roles with pagination")
    void testSearchRoles_Success() {
        // Arrange
        RoleSearchQuery query = new RoleSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("createdTime");
        query.setSortDir("desc");
        query.setName("ROLE");

        Role role1 = new Role();
        role1.setId(UUID.randomUUID());
        role1.setName("ROLE_ADMIN");
        role1.setDescription("Admin Role");

        Role role2 = new Role();
        role2.setId(UUID.randomUUID());
        role2.setName("ROLE_USER");
        role2.setDescription("User Role");

        List<Role> roleList = List.of(role1, role2);
        Page<Role> rolePage = new PageImpl<>(roleList, PageRequest.of(0, 20), 2);

        RoleOutVo roleVo1 = new RoleOutVo();
        roleVo1.setName("ROLE_ADMIN");
        roleVo1.setDescription("Admin Role");

        RoleOutVo roleVo2 = new RoleOutVo();
        roleVo2.setName("ROLE_USER");
        roleVo2.setDescription("User Role");

        when(roleDataAccess.searchRoles(any(RoleSearchQuery.class))).thenReturn(rolePage);
        when(roleMapper.toVo(role1)).thenReturn(roleVo1);
        when(roleMapper.toVo(role2)).thenReturn(roleVo2);

        // Act
        PageResult<RoleOutVo> result = roleService.searchRoles(query);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(2L, result.getTotalElements());
        assertEquals(0, result.getCurrentPage());
        assertEquals(20, result.getPageSize());
        assertTrue(result.getIsFirst());
        assertTrue(result.getIsLast());
        verify(roleDataAccess).searchRoles(any(RoleSearchQuery.class));
    }

    @Test
    @DisplayName("Should throw Exception for invalid sort field in role search")
    void testSearchRoles_InvalidSortField() {
        // Arrange
        RoleSearchQuery query = new RoleSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("invalidField");
        query.setSortDir("desc");

        // Act & Assert
        assertThrows(AppException.class, () -> roleService.searchRoles(query));
    }

    @Test
    @DisplayName("Should throw Exception for invalid sort direction in role search")
    void testSearchRoles_InvalidSortDirection() {
        // Arrange
        RoleSearchQuery query = new RoleSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("createdTime");
        query.setSortDir("invalid");

        // Act & Assert
        assertThrows(AppException.class, () -> roleService.searchRoles(query));
    }
}
