package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Vo.Search.RoleSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IFunctionQueryService;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.PermissionVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Vo.UserVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
@ApiControllerTag(name = "Roles", description = "角色與權限管理相關 API")
public class RoleController {
    private final IRoleService roleService;
    private final IFunctionQueryService functionQueryService;
    private final IUserService userService;

    @PostMapping("/add")
    @RequirePermission("Edit")
    @Deprecated
    @ApiOperationBadRequest(summary = "新增角色", description = "Deprecated: 請改用 /role/addWithFunctions。此 API 只建立角色，不會同步 functionIds 權限綁定。", deprecated = true)
    public ResponseType<RoleOutVo> addRole(@RequestBody RoleOutVo role) {
        return ResponseType.Success(roleService.addRole(role), "角色新增成功");
    }

    @PostMapping("/addWithFunctions")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增角色並綁定權限", description = "建立角色並同步綁定 functionIds 權限。functionIds = null 時不處理權限；functionIds = [] 時清空權限；functionIds 有值時，以該清單為準綁定角色權限。")
    public ResponseType<RoleOutVo> addRoleWithFunctions(@RequestBody RoleOutVo role) {
        return ResponseType.Success(roleService.addRoleWithFunctions(role), "角色新增成功");
    }

    @PostMapping("/get")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得角色列表", description = "回傳所有角色。")
    public ResponseType<List<RoleOutVo>> getRole() {
        return ResponseType.Success(roleService.getRole(), "角色查詢成功");
    }

    @PostMapping("/update")
    @RequirePermission("Edit")
    @Deprecated
    @ApiOperationBadRequest(summary = "更新角色", description = "Deprecated: 請改用 /role/updateWithFunctions。此 API 只更新角色資料，不會同步 functionIds 權限綁定。", deprecated = true)
    public ResponseType<RoleOutVo> updateRole(@RequestBody RoleOutVo role) {
        return ResponseType.Success(roleService.updateRole(role), "角色更新成功");
    }

    @PostMapping("/updateWithFunctions")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新角色並同步權限", description = "更新角色資料並同步 functionIds 權限。functionIds = null 時保留既有權限不變；functionIds = [] 時清空該角色所有權限；functionIds 有值時，以該清單覆蓋該角色權限。")
    public ResponseType<RoleOutVo> updateRoleWithFunctions(@RequestBody RoleOutVo role) {
        return ResponseType.Success(roleService.updateRoleWithFunctions(role), "角色更新成功");
    }

    @PostMapping("/delete")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除角色", description = "刪除一筆角色。")
    public ResponseType<RoleOutVo> deleteRole(@RequestBody RoleOutVo role) {
        roleService.deleteRole(role);
        return ResponseType.Success(role, "角色刪除成功");
    }

    @PostMapping("/roleBindFunction")
    @RequirePermission("Edit")
    @Deprecated
    @ApiOperationBadRequest(summary = "角色綁定功能", description = "Deprecated: 請改用 /role/updateWithFunctions，透過 functionIds 一次同步角色權限。", deprecated = true)
    public ResponseType<RoleOutVo> roleBindFunction(@RequestBody PermissionVo permissionVo) {
        roleService.roleBindFunction(permissionVo.getRole(), permissionVo.getFunctionList());
        return ResponseType.Success(roleService.getRoleById(permissionVo.getRole()), "角色綁定功能成功");
    }

    @PostMapping("/functionBindRole")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "功能綁定角色", description = "指派角色給一筆功能。")
    public ResponseType<FunctionVo> functionBindRole(@RequestBody PermissionVo permissionVo) {
        roleService.functionBindRole(permissionVo.getFunction(), permissionVo.getRoleList());
        return ResponseType.Success(functionQueryService.getFunctionById(permissionVo.getFunction()), "功能綁定角色成功");
    }

    @PostMapping("/roleBindUser")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "角色綁定使用者", description = "指派使用者給一筆角色。")
    public ResponseType<RoleOutVo> roleBindUser(@RequestBody PermissionVo permissionVo) {
        roleService.roleBindUser(permissionVo.getRole(), permissionVo.getUserList());
        return ResponseType.Success(roleService.getRoleById(permissionVo.getRole()), "角色綁定使用者成功");
    }

    @PostMapping("/userBindRole")
    @RequirePermission("Edit")
    @Deprecated
    @ApiOperationBadRequest(
            summary = "使用者綁定角色（已棄用）",
            description = "Deprecated: 請改用 POST /users/{userId}/roles/rebind，提供更清晰的完整覆蓋語意。",
            deprecated = true
    )
    public ResponseType<UserVo> userBindRole(@RequestBody PermissionVo permissionVo) {
        roleService.userBindRole(permissionVo.getUser(), permissionVo.getRoleList());
        return ResponseType.Success(userService.getUserById(permissionVo.getUser()), "使用者綁定角色成功");
    }

    @PostMapping("/roleUnbindUser")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "角色解除綁定使用者", description = "將使用者從角色中移除。")
    public ResponseType<RoleOutVo> roleUnbindUser(@RequestBody PermissionVo permissionVo) {
        roleService.roleUnbindUser(permissionVo.getRole(), permissionVo.getUserList());
        return ResponseType.Success(roleService.getRoleById(permissionVo.getRole()), "角色解除綁定使用者成功");
    }

    @PostMapping("/userUnbindRole")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "使用者解除綁定角色", description = "將角色從使用者中移除。")
    public ResponseType<UserVo> userUnbindRole(@RequestBody PermissionVo permissionVo) {
        roleService.userUnbindRole(permissionVo.getUser(), permissionVo.getRoleList());
        return ResponseType.Success(userService.getUserById(permissionVo.getUser()), "使用者解除綁定角色成功");
    }

    @PostMapping("/roleUnbindFunction")
    @RequirePermission("Edit")
    @Deprecated
    @ApiOperationBadRequest(summary = "角色解除綁定功能", description = "Deprecated: 請改用 /role/updateWithFunctions，透過 functionIds 一次同步角色權限；若要清空權限請傳 functionIds = []。", deprecated = true)
    public ResponseType<RoleOutVo> roleUnbindFunction(@RequestBody PermissionVo permissionVo) {
        roleService.roleUnbindFunction(permissionVo.getRole(), permissionVo.getFunctionList());
        return ResponseType.Success(roleService.getRoleById(permissionVo.getRole()), "角色解除綁定功能成功");
    }

    @PostMapping("/functionUnbindRole")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "功能解除綁定角色", description = "將角色從功能中移除。")
    public ResponseType<FunctionVo> functionUnbindRole(@RequestBody PermissionVo permissionVo) {
        roleService.functionUnbindRole(permissionVo.getFunction(), permissionVo.getRoleList());
        return ResponseType.Success(functionQueryService.getFunctionById(permissionVo.getFunction()), "功能解除綁定角色成功");
    }

    @PostMapping("/getFunctionByRole")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "依角色取得功能列表", description = "取得指派給指定角色的功能。")
    public ResponseType<List<FunctionVo>> getFunctionByRole(@RequestBody RoleOutVo role) {
        return ResponseType.Success(roleService.getFunctionByRole(role.getId().toString()), "功能查詢成功");
    }

    @PostMapping("/getRoleByFunction")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "依功能取得角色列表", description = "取得指派給指定功能的角色。")
    public ResponseType<List<RoleOutVo>> getRoleByFunction(@RequestBody FunctionVo function) {
        return ResponseType.Success(roleService.getRoleByFunction(function.getId()), "角色查詢成功");
    }

    @PostMapping("/getRoleByUser")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "依使用者取得角色列表", description = "取得指派給指定使用者的角色。")
    public ResponseType<List<RoleOutVo>> getRoleByUser(@RequestBody UserVo user) {
        return ResponseType.Success(roleService.getRoleByUser(user.getId()), "角色查詢成功");
    }

    @PostMapping("/getUserByRole")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "依角色取得使用者列表", description = "取得指派給指定角色的使用者。")
    public ResponseType<List<UserVo>> getUserByRole(@RequestBody RoleOutVo role) {
        return ResponseType.Success(roleService.getUserByRole(role.getId().toString()), "使用者查詢成功");
    }
    
    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "分頁搜尋角色", description = "搜尋角色並回傳分頁結果，支援多種查詢條件與排序")
    public ResponseType<PageResult<RoleOutVo>> searchRoles(@Valid @RequestBody RoleSearchQuery query) {
        PageResult<RoleOutVo> result = roleService.searchRoles(query);
        return ResponseType.Success(result, "角色查詢成功");
    }
}