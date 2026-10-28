package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.IFunctionQueryService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.UserVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalPermissionValidatorImplTest {

    @Mock
    private IFunctionQueryService functionQueryService;

    @Mock
    private IUserService userService;

    @InjectMocks
    private LocalPermissionValidatorImpl validator;

    private UserVo adminUser;
    private UserVo normalUser;
    private FunctionVo func1;
    private FunctionVo func2;
    private FunctionVo func3;

    @BeforeEach
    void setUp() {
        adminUser = new UserVo();
        adminUser.setEmail("admin@example.com");
        adminUser.setRoleNames(List.of("ROLE_ADMIN"));

        func1 = new FunctionVo();
        func1.setId("f1");
        func1.setName("Module");

        func2 = new FunctionVo();
        func2.setId("f2");
        func2.setName("Layer");
        func2.setParent("f1");

        func3 = new FunctionVo();
        func3.setId("f3");
        func3.setName("Action");
        func3.setParent("f2");

        normalUser = new UserVo();
        normalUser.setEmail("user@example.com");
        normalUser.setRoleNames(List.of("ROLE_USER"));
        normalUser.setPermissions(List.of(func3));
    }

    @Test
    @DisplayName("Validate should return false when email is null or blank")
    void testValidate_NullOrBlankEmail() {
        assertFalse(validator.validate(null, "1", "2", "3"));
        assertFalse(validator.validate("   ", "1", "2", "3"));
    }

    @Test
    @DisplayName("Validate should return false when user not found")
    void testValidate_UserNotFound() {
        when(userService.getUserWithFullPermissionsByEmail("unknown@example.com")).thenReturn(null);
        assertFalse(validator.validate("unknown@example.com", "1", "2", "3"));
    }

    @Test
    @DisplayName("Validate should return true for admin user and cache result")
    void testValidate_AdminUser() {
        when(userService.getUserWithFullPermissionsByEmail("admin@example.com")).thenReturn(adminUser);

        assertTrue(validator.validate("admin@example.com", "Module", "Layer", "Action"));
        // 第二次呼叫走快取
        assertTrue(validator.validate("admin@example.com", "Module", "Layer", "Action"));
        verify(userService).getUserWithFullPermissionsByEmail("admin@example.com");
    }

    @Test
    @DisplayName("Validate should return true when normal user has matching permission")
    void testValidate_NormalUser_Match() {
        when(userService.getUserWithFullPermissionsByEmail("user@example.com")).thenReturn(normalUser);
        when(functionQueryService.getFunctionByPath("Module", "Layer", "Action")).thenReturn(func3);

        assertTrue(validator.validate("user@example.com", "Module", "Layer", "Action"));
    }

    @Test
    @DisplayName("Validate should return false when normal user does not have permission")
    void testValidate_NormalUser_NoMatch() {
        when(userService.getUserWithFullPermissionsByEmail("user@example.com")).thenReturn(normalUser);
        FunctionVo otherFunc = new FunctionVo();
        otherFunc.setId("other");
        when(functionQueryService.getFunctionByPath("Module", "Layer", "Other")).thenReturn(otherFunc);

        assertFalse(validator.validate("user@example.com", "Module", "Layer", "Other"));
    }

    @Test
    @DisplayName("GetUserPermissions should return wild card for admin")
    void testGetUserPermissions_Admin() {
        when(userService.getUserWithFullPermissionsByEmail("admin@example.com")).thenReturn(adminUser);

        Set<String> perms = validator.getUserPermissions("admin@example.com");
        assertTrue(perms.contains("*"));
    }

    @Test
    @DisplayName("GetUserPermissions should return formatted permission paths for normal user")
    void testGetUserPermissions_NormalUser() {
        when(userService.getUserWithFullPermissionsByEmail("user@example.com")).thenReturn(normalUser);
        when(functionQueryService.getFunction()).thenReturn(List.of(func1, func2, func3));

        Set<String> perms = validator.getUserPermissions("user@example.com");
        assertTrue(perms.contains("Module:Layer:Action"));
    }

    @Test
    @DisplayName("GetUserPermissions should return empty set for blank email or missing user")
    void testGetUserPermissions_EmptyOrMissing() {
        assertTrue(validator.getUserPermissions("").isEmpty());
        when(userService.getUserWithFullPermissionsByEmail(anyString())).thenReturn(null);
        assertTrue(validator.getUserPermissions("missing@example.com").isEmpty());
    }

    @Test
    @DisplayName("Validate should use preloaded userPermsCache when available")
    void testValidate_UsesUserPermsCacheFastPath() {
        when(userService.getUserWithFullPermissionsByEmail("user@example.com")).thenReturn(normalUser);
        when(functionQueryService.getFunction()).thenReturn(List.of(func1, func2, func3));

        Set<String> perms = validator.getUserPermissions("user@example.com");
        assertTrue(perms.contains("Module:Layer:Action"));

        assertTrue(validator.validate("user@example.com", "Module", "Layer", "Action"));
        assertFalse(validator.validate("user@example.com", "Module", "Layer", "Other"));
    }

    @Test
    @DisplayName("Validate should recognize Chinese system admin role name")
    void testValidate_ChineseSystemAdminRole() {
        UserVo sysAdmin = new UserVo();
        sysAdmin.setEmail("sysadmin@example.com");
        sysAdmin.setRoleNames(List.of("系統管理員"));
        when(userService.getUserWithFullPermissionsByEmail("sysadmin@example.com")).thenReturn(sysAdmin);

        assertTrue(validator.validate("sysadmin@example.com", "Any", "Module", "Action"));
    }
}
