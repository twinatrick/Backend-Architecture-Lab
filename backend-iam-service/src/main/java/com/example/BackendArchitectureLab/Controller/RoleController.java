package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiBody;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiPath;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiQuery;
import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Dto.Request.BatchStringIdsRequest;
import com.example.BackendArchitectureLab.Dto.Query.RoleSearchQuery;
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
import com.example.BackendArchitectureLab.Vo.RoleVo;
import com.example.BackendArchitectureLab.Vo.UserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
@ApiControllerTag(name = "Roles", description = "角色與權限管理相關 API")
public class RoleController {
    private final IRoleService roleService;
    private final IFunctionQueryService functionQueryService;
    private final IUserService userService;

    @PostMapping
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增角色並綁定權限", description = "建立角色並同步綁定 functionIds 權限。functionIds = null 時不處理權限；functionIds = [] 時清空權限；functionIds 有值時，以該清單為準綁定角色權限。")
    public ResponseType<RoleVo> createRole(
            @ApiBody(description = "角色資料") RoleVo role) {
        return ResponseType.Success(roleService.addRoleWithFunctions(role), "角色新增成功");
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("View")
    @ApiOperationOk(summary = "串流分塊取得角色列表", description = "透過 SSE 串流分塊取得角色列表，每批次預設 250 筆")
    public Flux<ServerSentEvent<List<RoleVo>>> streamRoles(
            @ApiQuery(name = "chunkSize", defaultValue = "250", description = "串流分塊大小", example = "250") int chunkSize) {
        return roleService.streamRolesChunked(chunkSize);
    }

    @GetMapping("/{id}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "依 ID 取得角色", description = "根據角色 ID 取得角色詳細資訊與綁定的權限列表。")
    public ResponseType<RoleVo> getRoleById(
            @ApiPath(description = "角色唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") String id) {
        return ResponseType.Success(roleService.getRoleById(id), "角色查詢成功");
    }

    @PostMapping("/batch-query")
    @RequirePermission("View")
    @ApiOperationOk(summary = "批次依 ID 取得角色清單", description = "傳入角色 ID 清單（最多 100 筆），優先命中細粒度快取，未命中批次查庫並回填快取。")
    public ResponseType<BatchResultVo<RoleVo>> getRolesByIds(
            @ApiBody(description = "批次角色 ID 請求") BatchStringIdsRequest request) {
        return ResponseType.Success(roleService.getRolesByIds(request.getIds()), "批次角色查詢成功");
    }

    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "分頁搜尋角色", description = "搜尋角色並回傳分頁結果，支援多種查詢條件與排序")
    public ResponseType<PageResult<RoleVo>> searchRoles(
            @ApiBody(description = "分頁搜尋條件") RoleSearchQuery query) {
        PageResult<RoleVo> result = roleService.searchRoles(query);
        return ResponseType.Success(result, "角色查詢成功");
    }

    @PutMapping("/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新角色並同步權限", description = "更新指定 ID 之角色資料並同步 functionIds 權限。functionIds = null 時保留既有權限不變；functionIds = [] 時清空該角色所有權限；functionIds 有值時，以該清單覆蓋該角色權限。")
    public ResponseType<RoleVo> updateRole(
            @ApiPath(description = "角色唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") String id,
            @ApiBody(description = "角色更新資料") RoleVo role) {
        role.setId(UUID.fromString(id));
        return ResponseType.Success(roleService.updateRoleWithFunctions(role), "角色更新成功");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除角色", description = "刪除指定 ID 之角色。")
    public ResponseType<String> deleteRole(
            @ApiPath(description = "角色唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") String id) {
        RoleVo role = new RoleVo();
        role.setId(UUID.fromString(id));
        roleService.deleteRole(role);
        return ResponseType.Success("角色刪除成功");
    }

    @PostMapping("/functionBindRole")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "功能綁定角色", description = "指派角色給一筆功能。")
    public ResponseType<FunctionVo> functionBindRole(
            @ApiBody(description = "功能角色綁定資料") PermissionVo permissionVo) {
        roleService.functionBindRole(permissionVo.getFunction(), permissionVo.getRoleList());
        return ResponseType.Success(functionQueryService.getFunctionById(permissionVo.getFunction()), "功能綁定角色成功");
    }

    @PostMapping("/roleBindUser")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "角色綁定使用者", description = "指派使用者給一筆角色。")
    public ResponseType<RoleVo> roleBindUser(
            @ApiBody(description = "角色使用者綁定資料") PermissionVo permissionVo) {
        roleService.roleBindUser(permissionVo.getRole(), permissionVo.getUserList());
        return ResponseType.Success(roleService.getRoleById(permissionVo.getRole()), "角色綁定使用者成功");
    }

    @PostMapping("/roleUnbindUser")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "角色解除綁定使用者", description = "將使用者從角色中移除。")
    public ResponseType<RoleVo> roleUnbindUser(
            @ApiBody(description = "角色使用者解綁資料") PermissionVo permissionVo) {
        roleService.roleUnbindUser(permissionVo.getRole(), permissionVo.getUserList());
        return ResponseType.Success(roleService.getRoleById(permissionVo.getRole()), "角色解除綁定使用者成功");
    }

    @PostMapping("/userUnbindRole")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "使用者解除綁定角色", description = "將角色從使用者中移除。")
    public ResponseType<UserVo> userUnbindRole(
            @ApiBody(description = "使用者角色解綁資料") PermissionVo permissionVo) {
        roleService.userUnbindRole(permissionVo.getUser(), permissionVo.getRoleList());
        return ResponseType.Success(userService.getUserById(permissionVo.getUser()), "使用者解除綁定角色成功");
    }

    @PostMapping("/functionUnbindRole")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "功能解除綁定角色", description = "將角色從功能中移除。")
    public ResponseType<FunctionVo> functionUnbindRole(
            @ApiBody(description = "功能角色解綁資料") PermissionVo permissionVo) {
        roleService.functionUnbindRole(permissionVo.getFunction(), permissionVo.getRoleList());
        return ResponseType.Success(functionQueryService.getFunctionById(permissionVo.getFunction()), "功能解除綁定角色成功");
    }

    @PostMapping("/getFunctionByRole")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "依角色取得功能列表", description = "取得指派給指定角色的功能。")
    public ResponseType<List<FunctionVo>> getFunctionByRole(
            @ApiBody(description = "角色資料") RoleVo role) {
        return ResponseType.Success(roleService.getFunctionByRole(role.getId().toString()), "功能查詢成功");
    }

    @PostMapping("/getRoleByFunction")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "依功能取得角色列表", description = "取得指派給指定功能的角色。")
    public ResponseType<List<RoleVo>> getRoleByFunction(
            @ApiBody(description = "功能資料") FunctionVo function) {
        return ResponseType.Success(roleService.getRoleByFunction(function.getId()), "角色查詢成功");
    }

    @PostMapping("/getRoleByUser")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "依使用者取得角色列表", description = "取得指派給指定使用者的角色。")
    public ResponseType<List<RoleVo>> getRoleByUser(
            @ApiBody(description = "使用者資料") UserVo user) {
        return ResponseType.Success(roleService.getRoleByUser(user.getId()), "角色查詢成功");
    }

    @PostMapping("/getUserByRole")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "依角色取得使用者列表", description = "取得指派給指定角色的使用者。")
    public ResponseType<List<UserVo>> getUserByRole(
            @ApiBody(description = "角色資料") RoleVo role) {
        return ResponseType.Success(roleService.getUserByRole(role.getId().toString()), "使用者查詢成功");
    }
}