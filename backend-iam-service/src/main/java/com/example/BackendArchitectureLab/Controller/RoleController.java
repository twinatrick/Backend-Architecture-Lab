package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Dto.Vo.Search.RoleSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IFunctionService;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Dto.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Dto.Vo.PermissionVo;
import com.example.BackendArchitectureLab.Dto.Vo.ResponseType;
import com.example.BackendArchitectureLab.Dto.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/role")
@ApiControllerTag(name = "Roles", description = "Backend API endpoints - Role and permission management")
public class RoleController {
    @Autowired
    private IRoleService roleService;
    @Autowired
    private IFunctionService functionService;
    @Autowired
    private IUserService userService;

    @PostMapping("/add")
    @RequirePermission({"System", "Role", "Edit"})
    @Deprecated
    @ApiOperationBadRequest(summary = "Add role", description = "Deprecated: 請改用 /role/addWithFunctions。此 API 只建立角色，不會同步 functionIds 權限綁定。")
    @Operation(deprecated = true, summary = "Add role", description = "Deprecated: 請改用 /role/addWithFunctions。此 API 只建立角色，不會同步 functionIds 權限綁定。")
    public ResponseType<RoleOutVo> addRole(@RequestBody RoleOutVo role) {
        return ResponseType.Success(roleService.addRole(role), "Role added successfully");
    }

    @PostMapping("/addWithFunctions")
    @RequirePermission({"System", "Role", "Edit"})
    @ApiOperationBadRequest(summary = "Add role with functions", description = "建立角色並同步綁定 functionIds 權限。functionIds = null 時不處理權限；functionIds = [] 時清空權限；functionIds 有值時，以該清單為準綁定角色權限。")
    public ResponseType<RoleOutVo> addRoleWithFunctions(@RequestBody RoleOutVo role) {
        return ResponseType.Success(roleService.addRoleWithFunctions(role), "Role added with functions successfully");
    }

    @PostMapping("/get")
    @RequirePermission({"System", "Role", "View"})
    @ApiOperationOk(summary = "Get roles", description = "Returns all roles.")
    public ResponseType<List<RoleOutVo>> getRole() {
        return ResponseType.Success(roleService.getRole(), "Role fetched successfully");
    }

    @PostMapping("/update")
    @RequirePermission({"System", "Role", "Edit"})
    @Deprecated
    @ApiOperationBadRequest(summary = "Update role", description = "Deprecated: 請改用 /role/updateWithFunctions。此 API 只更新角色資料，不會同步 functionIds 權限綁定。")
    @Operation(deprecated = true, summary = "Update role", description = "Deprecated: 請改用 /role/updateWithFunctions。此 API 只更新角色資料，不會同步 functionIds 權限綁定。")
    public ResponseType<RoleOutVo> updateRole(@RequestBody RoleOutVo role) {
        return ResponseType.Success(roleService.updateRole(role), "Role updated successfully");
    }

    @PostMapping("/updateWithFunctions")
    @RequirePermission({"System", "Role", "Edit"})
    @ApiOperationBadRequest(summary = "Update role with functions", description = "更新角色資料並同步 functionIds 權限。functionIds = null 時保留既有權限不變；functionIds = [] 時清空該角色所有權限；functionIds 有值時，以該清單覆蓋該角色權限。")
    public ResponseType<RoleOutVo> updateRoleWithFunctions(@RequestBody RoleOutVo role) {
        return ResponseType.Success(roleService.updateRoleWithFunctions(role), "Role updated with functions successfully");
    }

    @PostMapping("/delete")
    @RequirePermission({"System", "Role", "Edit"})
    @ApiOperationBadRequest(summary = "Delete role", description = "Deletes a role.")
    public ResponseType<RoleOutVo> deleteRole(@RequestBody RoleOutVo role) {
        roleService.deleteRole(role);
        return ResponseType.Success(role, "Role deleted successfully");
    }

    @PostMapping("/roleBindFunction")
    @RequirePermission({"System", "Role", "Edit"})
    @Deprecated
    @ApiOperationBadRequest(summary = "Bind role to functions", description = "Deprecated: 請改用 /role/updateWithFunctions，透過 functionIds 一次同步角色權限。")
    @Operation(deprecated = true, summary = "Bind role to functions", description = "Deprecated: 請改用 /role/updateWithFunctions，透過 functionIds 一次同步角色權限。")
    public ResponseType<RoleOutVo> roleBindFunction(@RequestBody PermissionVo permissionVo) {
        roleService.roleBindFunction(permissionVo.getRole(), permissionVo.getFunctionList());
        return ResponseType.Success(roleService.getRoleById(permissionVo.getRole()), "Role bound to function successfully");
    }

    @PostMapping("/functionBindRole")
    @RequirePermission({"System", "Role", "Edit"})
    @ApiOperationBadRequest(summary = "Bind function to roles", description = "Assigns roles to a function.")
    public ResponseType<FunctionVo> functionBindRole(@RequestBody PermissionVo permissionVo) {
        roleService.functionBindRole(permissionVo.getFunction(), permissionVo.getRoleList());
        return ResponseType.Success(functionService.getFunctionById(permissionVo.getFunction()), "Function bound to role successfully");
    }

    @PostMapping("/roleBindUser")
    @RequirePermission({"System", "Role", "Edit"})
    @ApiOperationBadRequest(summary = "Bind role to users", description = "Assigns users to a role.")
    public ResponseType<RoleOutVo> roleBindUser(@RequestBody PermissionVo permissionVo) {
        roleService.roleBindUser(permissionVo.getRole(), permissionVo.getUserList());
        return ResponseType.Success(roleService.getRoleById(permissionVo.getRole()), "Role bound to user successfully");
    }

    @PostMapping("/userBindRole")
    @RequirePermission({"System", "Role", "Edit"})
    @Deprecated
    @ApiOperationBadRequest(
            summary = "Bind user to roles (deprecated)",
            description = "Deprecated: 請改用 POST /users/{userId}/roles/rebind，提供更清晰的完整覆蓋語意。"
    )
    @Operation(deprecated = true)
    public ResponseType<UserVo> userBindRole(@RequestBody PermissionVo permissionVo) {
        roleService.userBindRole(permissionVo.getUser(), permissionVo.getRoleList());
        return ResponseType.Success(userService.getUserById(permissionVo.getUser()), "User bound to role successfully");
    }

    @PostMapping("/roleUnbindUser")
    @RequirePermission({"System", "Role", "Edit"})
    @ApiOperationBadRequest(summary = "Unbind role from users", description = "Removes users from a role.")
    public ResponseType<RoleOutVo> roleUnbindUser(@RequestBody PermissionVo permissionVo) {
        roleService.roleUnbindUser(permissionVo.getRole(), permissionVo.getUserList());
        return ResponseType.Success(roleService.getRoleById(permissionVo.getRole()), "Role unbound from user successfully");
    }

    @PostMapping("/userUnbindRole")
    @RequirePermission({"System", "Role", "Edit"})
    @ApiOperationBadRequest(summary = "Unbind user from roles", description = "Removes roles from a user.")
    public ResponseType<UserVo> userUnbindRole(@RequestBody PermissionVo permissionVo) {
        roleService.userUnbindRole(permissionVo.getUser(), permissionVo.getRoleList());
        return ResponseType.Success(userService.getUserById(permissionVo.getUser()), "User unbound from role successfully");
    }

    @PostMapping("/roleUnbindFunction")
    @RequirePermission({"System", "Role", "Edit"})
    @Deprecated
    @ApiOperationBadRequest(summary = "Unbind role from functions", description = "Deprecated: 請改用 /role/updateWithFunctions，透過 functionIds 一次同步角色權限；若要清空權限請傳 functionIds = []。")
    @Operation(deprecated = true, summary = "Unbind role from functions", description = "Deprecated: 請改用 /role/updateWithFunctions，透過 functionIds 一次同步角色權限；若要清空權限請傳 functionIds = []。")
    public ResponseType<RoleOutVo> roleUnbindFunction(@RequestBody PermissionVo permissionVo) {
        roleService.roleUnbindFunction(permissionVo.getRole(), permissionVo.getFunctionList());
        return ResponseType.Success(roleService.getRoleById(permissionVo.getRole()), "Role unbound from function successfully");
    }

    @PostMapping("/functionUnbindRole")
    @RequirePermission({"System", "Role", "Edit"})
    @ApiOperationBadRequest(summary = "Unbind function from roles", description = "Removes roles from a function.")
    public ResponseType<FunctionVo> functionUnbindRole(@RequestBody PermissionVo permissionVo) {
        roleService.functionUnbindRole(permissionVo.getFunction(), permissionVo.getRoleList());
        return ResponseType.Success(functionService.getFunctionById(permissionVo.getFunction()), "Function unbound from role successfully");
    }

    @PostMapping("/getFunctionByRole")
    @RequirePermission({"System", "Role", "View"})
    @ApiOperationBadRequest(summary = "Get functions by role", description = "Returns functions assigned to a role.")
    public ResponseType<List<FunctionVo>> getFunctionByRole(@RequestBody RoleOutVo role) {
        return ResponseType.Success(roleService.getFunctionByRole(role.getId().toString()), "Function fetched successfully");
    }

    @PostMapping("/getRoleByFunction")
    @RequirePermission({"System", "Role", "View"})
    @ApiOperationBadRequest(summary = "Get roles by function", description = "Returns roles assigned to a function.")
    public ResponseType<List<RoleOutVo>> getRoleByFunction(@RequestBody FunctionVo function) {
        return ResponseType.Success(roleService.getRoleByFunction(function.getId()), "Role fetched successfully");
    }

    @PostMapping("/getRoleByUser")
    @RequirePermission({"System", "Role", "View"})
    @ApiOperationBadRequest(summary = "Get roles by user", description = "Returns roles assigned to a user.")
    public ResponseType<List<RoleOutVo>> getRoleByUser(@RequestBody UserVo user) {
        return ResponseType.Success(roleService.getRoleByUser(user.getId()), "Role fetched successfully");
    }

    @PostMapping("/getUserByRole")
    @RequirePermission({"System", "Role", "View"})
    @ApiOperationBadRequest(summary = "Get users by role", description = "Returns users assigned to a role.")
    public ResponseType<List<UserVo>> getUserByRole(@RequestBody RoleOutVo role) {
        return ResponseType.Success(roleService.getUserByRole(role.getId().toString()), "User fetched successfully");
    }
    
    @PostMapping("/search")
    @RequirePermission({"System", "Role", "View"})
    @ApiOperationOk(summary = "Search roles with pagination", description = "搜尋角色並回傳分頁結果，支援多種查詢條件與排序")
    public ResponseType<PageResult<RoleOutVo>> searchRoles(@Valid @RequestBody RoleSearchQuery query) {
        PageResult<RoleOutVo> result = roleService.searchRoles(query);
        return ResponseType.Success(result, "Roles fetched successfully");
    }
}
