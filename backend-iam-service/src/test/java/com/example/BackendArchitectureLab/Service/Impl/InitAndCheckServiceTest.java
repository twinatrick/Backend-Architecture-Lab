package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Service.IFunctionCommandService;
import com.example.BackendArchitectureLab.Service.IFunctionQueryService;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Service.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InitAndCheckServiceTest {

    @Mock
    private IRoleService roleService;

    @Mock
    private IUserService userService;

    @Mock
    private IFunctionQueryService functionQueryService;

    @Mock
    private IFunctionCommandService functionCommandService;

    @InjectMocks
    private InitAndCheckService initAndCheckService;

    private RoleOutVo adminRole;
    private RoleOutVo userRole;
    private UserVo adminUser;
    private FunctionVo rootFunction;
    private FunctionVo childFunction;
    private FunctionVo leafFunction;

    @BeforeEach
    void setUp() {
        adminRole = new RoleOutVo();
        adminRole.setId(UUID.randomUUID());
        adminRole.setName("admin");

        userRole = new RoleOutVo();
        userRole.setId(UUID.randomUUID());
        userRole.setName("user");

        adminUser = new UserVo();
        adminUser.setId(UUID.randomUUID().toString());
        adminUser.setEmail("admin");
        adminUser.setPassword("admin");

        rootFunction = new FunctionVo();
        rootFunction.setId(UUID.randomUUID().toString());
        rootFunction.setName("System");

        childFunction = new FunctionVo();
        childFunction.setId(UUID.randomUUID().toString());
        childFunction.setName("User");
        childFunction.setParent(rootFunction.getId());

        leafFunction = new FunctionVo();
        leafFunction.setId(UUID.randomUUID().toString());
        leafFunction.setName("View");
        leafFunction.setParent(childFunction.getId());
    }

    // ==================== initAndCheck ====================

    @Test
    @DisplayName("Should call all check methods")
    void testInitAndCheck() {
        when(roleService.getRole()).thenReturn(List.of(adminRole, userRole));
        when(roleService.getRoleByName("admin")).thenReturn(adminRole);
        when(roleService.getRoleByName("user")).thenReturn(userRole);
        when(userService.getUserByEmail("admin")).thenReturn(List.of(adminUser));
        when(functionQueryService.getFunctionByPath(anyString(), anyString(), anyString())).thenReturn(null);
        when(functionCommandService.addFunction(any(FunctionVo.class))).thenAnswer(invocation -> {
            FunctionVo f = invocation.getArgument(0);
            f.setId(UUID.randomUUID().toString());
            return f;
        });
        when(functionQueryService.getFunction()).thenReturn(List.of(rootFunction, childFunction, leafFunction));
        doNothing().when(roleService).roleBindFunction(anyString(), anyList());

        initAndCheckService.initAndCheck();

        verify(roleService, times(1)).getRole();
        verify(functionQueryService, atLeastOnce()).getFunction();
    }

    // ==================== checkRole ====================

    @Test
    @DisplayName("Should not create roles when both already exist")
    void testCheckRole_BothRolesExist() {
        when(roleService.getRole()).thenReturn(List.of(adminRole, userRole));
        when(roleService.getRoleByName("admin")).thenReturn(adminRole);
        when(roleService.getRoleByName("user")).thenReturn(userRole);
        when(userService.getUserByEmail("admin")).thenReturn(List.of(adminUser));

        initAndCheckService.checkRole();

        verify(roleService, never()).addRole(any());
        verify(roleService).roleBindUser(adminRole.getId().toString(), List.of(adminUser.getId()));
    }

    @Test
    @DisplayName("Should create admin role when missing")
    void testCheckRole_AdminRoleMissing() {
        when(roleService.getRole()).thenReturn(List.of(adminRole, userRole));
        when(roleService.getRoleByName("admin")).thenReturn(null).thenReturn(adminRole);
        when(roleService.getRoleByName("user")).thenReturn(userRole);
        when(userService.getUserByEmail("admin")).thenReturn(List.of(adminUser));

        initAndCheckService.checkRole();

        verify(roleService, times(1)).addRole(argThat(r -> "admin".equals(r.getName())));
        verify(roleService).roleBindUser(adminRole.getId().toString(), List.of(adminUser.getId()));
    }

    @Test
    @DisplayName("Should create user role when missing")
    void testCheckRole_UserRoleMissing() {
        when(roleService.getRole()).thenReturn(List.of(adminRole, userRole));
        when(roleService.getRoleByName("admin")).thenReturn(adminRole);
        when(roleService.getRoleByName("user")).thenReturn(null);
        when(userService.getUserByEmail("admin")).thenReturn(List.of(adminUser));

        initAndCheckService.checkRole();

        verify(roleService, times(1)).addRole(argThat(r -> "user".equals(r.getName())));
        verify(roleService).roleBindUser(adminRole.getId().toString(), List.of(adminUser.getId()));
    }

    @Test
    @DisplayName("Should create both roles when role list is empty")
    void testCheckRole_RoleListEmpty() {
        when(roleService.getRole()).thenReturn(new ArrayList<>());
        when(userService.getUserByEmail("admin")).thenReturn(List.of(adminUser));
        when(roleService.getRoleByName("admin")).thenReturn(adminRole);

        initAndCheckService.checkRole();

        verify(roleService, times(2)).addRole(any(RoleOutVo.class));
        verify(roleService).roleBindUser(adminRole.getId().toString(), List.of(adminUser.getId()));
    }

    @Test
    @DisplayName("Should create admin user when not found by email")
    void testCheckRole_AdminUserNotExists() {
        when(roleService.getRole()).thenReturn(List.of(adminRole, userRole));
        when(roleService.getRoleByName("admin")).thenReturn(adminRole);
        when(roleService.getRoleByName("user")).thenReturn(userRole);
        when(userService.getUserByEmail("admin")).thenReturn(new ArrayList<>())
                .thenReturn(List.of(adminUser));

        initAndCheckService.checkRole();

        verify(userService, times(1)).createUser(any(UserVo.class));
        verify(roleService).roleBindUser(adminRole.getId().toString(), List.of(adminUser.getId()));
    }

    @Test
    @DisplayName("Should skip role bind when admin role is null after retrieval")
    void testCheckRole_AdminRoleNullAfterRetrieval() {
        when(roleService.getRole()).thenReturn(List.of(adminRole, userRole));
        when(roleService.getRoleByName("admin")).thenReturn(adminRole);
        when(roleService.getRoleByName("user")).thenReturn(userRole);
        when(userService.getUserByEmail("admin")).thenReturn(List.of(adminUser));
        when(roleService.getRoleByName("admin")).thenReturn(null);

        initAndCheckService.checkRole();

        verify(roleService, never()).roleBindUser(anyString(), anyList());
    }

    // ==================== checkIsExist ====================

    @Test
    @DisplayName("Should return true when all three levels exist")
    void testCheckIsExist_AllExist() {
        when(functionQueryService.getFunctionByPath("System", "User", "View")).thenReturn(leafFunction);

        boolean result = initAndCheckService.checkIsExist("System", "User", "View");

        assertTrue(result);
        verify(functionQueryService).getFunctionByPath("System", "User", "View");
    }

    @Test
    @DisplayName("Should return false when first level is missing")
    void testCheckIsExist_FirstLevelMissing() {
        when(functionQueryService.getFunctionByPath("System", "User", "View")).thenReturn(null);

        boolean result = initAndCheckService.checkIsExist("System", "User", "View");

        assertFalse(result);
        verify(functionQueryService).getFunctionByPath("System", "User", "View");
    }

    @Test
    @DisplayName("Should return false when second level is missing")
    void testCheckIsExist_SecondLevelMissing() {
        when(functionQueryService.getFunctionByPath("System", "User", "View")).thenReturn(null);

        boolean result = initAndCheckService.checkIsExist("System", "User", "View");

        assertFalse(result);
        verify(functionQueryService).getFunctionByPath("System", "User", "View");
    }

    @Test
    @DisplayName("Should return false when third level is missing")
    void testCheckIsExist_ThirdLevelMissing() {
        when(functionQueryService.getFunctionByPath("System", "User", "View")).thenReturn(null);

        boolean result = initAndCheckService.checkIsExist("System", "User", "View");

        assertFalse(result);
        verify(functionQueryService).getFunctionByPath("System", "User", "View");
    }

    // ==================== checkFunctionBindDefaultRole ====================

    @Test
    @DisplayName("Should insert missing functions and bind leaves to admin role")
    void testCheckFunctionBindDefaultRole_InsertAndBind() {
        when(functionQueryService.getFunctionByPath(anyString(), anyString(), anyString())).thenReturn(null);
        when(functionCommandService.addFunction(any(FunctionVo.class))).thenAnswer(invocation -> {
            FunctionVo f = invocation.getArgument(0);
            f.setId(UUID.randomUUID().toString());
            return f;
        });
        List<FunctionVo> functionList = new ArrayList<>();
        FunctionVo f1 = new FunctionVo();
        f1.setId("f1");
        f1.setName("System");
        f1.setParent("");
        FunctionVo f2 = new FunctionVo();
        f2.setId("f2");
        f2.setName("User");
        f2.setParent("f1");
        FunctionVo f3 = new FunctionVo();
        f3.setId("f3");
        f3.setName("View");
        f3.setParent("f2");
        functionList.add(f1);
        functionList.add(f2);
        functionList.add(f3);
        when(functionQueryService.getFunction()).thenReturn(functionList);
        when(roleService.getRoleByName("admin")).thenReturn(adminRole);
        doNothing().when(roleService).roleBindFunction(anyString(), anyList());

        initAndCheckService.checkFunctionBindDefaultRole();

        verify(functionCommandService, atLeastOnce()).addFunction(any(FunctionVo.class));
        verify(roleService).roleBindFunction(eq(adminRole.getId().toString()), anyList());
    }

    @Test
    @DisplayName("Should skip binding when admin role is null")
    void testCheckFunctionBindDefaultRole_NullAdminRole() {
        when(functionQueryService.getFunctionByPath(anyString(), anyString(), anyString())).thenReturn(null);
        when(functionCommandService.addFunction(any(FunctionVo.class))).thenAnswer(invocation -> {
            FunctionVo f = invocation.getArgument(0);
            f.setId(UUID.randomUUID().toString());
            return f;
        });
        when(functionQueryService.getFunction()).thenReturn(List.of());
        when(roleService.getRoleByName("admin")).thenReturn(null);

        initAndCheckService.checkFunctionBindDefaultRole();

        verify(roleService, never()).roleBindFunction(anyString(), anyList());
    }

    @Test
    @DisplayName("Should not re-insert functions that already exist")
    void testCheckFunctionBindDefaultRole_AllExist() {
        when(functionQueryService.getFunctionByPath(anyString(), anyString(), anyString())).thenReturn(null);
        when(functionCommandService.addFunction(any(FunctionVo.class))).thenAnswer(invocation -> {
            FunctionVo f = invocation.getArgument(0);
            f.setId(UUID.randomUUID().toString());
            return f;
        });
        List<FunctionVo> functionList = new ArrayList<>();
        FunctionVo sysFunc = new FunctionVo();
        sysFunc.setId(rootFunction.getId());
        sysFunc.setName("System");
        sysFunc.setParent("");
        FunctionVo userFunc = new FunctionVo();
        userFunc.setId(childFunction.getId());
        userFunc.setName("User");
        userFunc.setParent(rootFunction.getId());
        FunctionVo viewFunc = new FunctionVo();
        viewFunc.setId(leafFunction.getId());
        viewFunc.setName("View");
        viewFunc.setParent(childFunction.getId());
        functionList.add(sysFunc);
        functionList.add(userFunc);
        functionList.add(viewFunc);
        when(functionQueryService.getFunction()).thenReturn(functionList);
        when(roleService.getRoleByName("admin")).thenReturn(adminRole);

        initAndCheckService.checkFunctionBindDefaultRole();

        verify(roleService).roleBindFunction(eq(adminRole.getId().toString()), anyList());
    }

    // ==================== insertFunctionByList ====================

    @Test
    @DisplayName("Should return immediately when function list is empty")
    void testInsertFunctionByList_EmptyList() {
        initAndCheckService.insertFunctionByList(new ArrayList<>(), "");

        verify(functionQueryService, never()).getFunctionByNameAndParent(anyString(), anyString());
        verify(functionCommandService, never()).addFunction(any());
    }

    @Test
    @DisplayName("Should create function and recurse when not found")
    void testInsertFunctionByList_CreateAndRecurse() {
        List<String> functionList = List.of("System", "User", "View");

        when(functionQueryService.getFunctionByNameAndParent("System", "")).thenReturn(null);
        when(functionQueryService.getFunctionByNameAndParent("User", "new-id")).thenReturn(null);
        when(functionQueryService.getFunctionByNameAndParent("View", "child-id")).thenReturn(null);
        when(functionCommandService.addFunction(any(FunctionVo.class)))
                .thenAnswer(invocation -> {
                    FunctionVo f = invocation.getArgument(0);
                    f.setId("new-id");
                    return f;
                })
                .thenAnswer(invocation -> {
                    FunctionVo f = invocation.getArgument(0);
                    f.setId("child-id");
                    return f;
                })
                .thenAnswer(invocation -> {
                    FunctionVo f = invocation.getArgument(0);
                    f.setId("leaf-id");
                    return f;
                });

        initAndCheckService.insertFunctionByList(functionList, "");

        verify(functionCommandService, times(3)).addFunction(any(FunctionVo.class));
    }

    @Test
    @DisplayName("Should reuse existing function and recurse deeper")
    void testInsertFunctionByList_ReuseExisting() {
        List<String> functionList = List.of("System", "User", "View");

        when(functionQueryService.getFunctionByNameAndParent("System", "")).thenReturn(rootFunction);
        when(functionQueryService.getFunctionByNameAndParent("User", rootFunction.getId())).thenReturn(childFunction);
        when(functionQueryService.getFunctionByNameAndParent("View", childFunction.getId())).thenReturn(null);
        FunctionVo createdLeaf = new FunctionVo();
        createdLeaf.setId("leaf-id");
        createdLeaf.setName("View");
        createdLeaf.setParent(childFunction.getId());
        when(functionCommandService.addFunction(argThat(f -> "View".equals(f.getName())))).thenReturn(createdLeaf);

        initAndCheckService.insertFunctionByList(functionList, "");

        verify(functionCommandService, times(1)).addFunction(any(FunctionVo.class));
        verify(functionQueryService).getFunctionByNameAndParent("User", rootFunction.getId());
        verify(functionQueryService).getFunctionByNameAndParent("View", childFunction.getId());
    }
}
