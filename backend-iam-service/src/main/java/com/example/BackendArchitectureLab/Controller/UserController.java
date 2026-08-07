package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Vo.Search.UserSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IAuthService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationAuth;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.UserRoleRebindRequest;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.UserVo;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/users")
@ApiControllerTag(name = "Users", description = "Backend API endpoints - User management")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private IUserService userService;

    @Autowired
    private IAuthService authService;


    @PostMapping(value = "/create")
    @RequirePermission("Create")
    @ApiOperationBadRequest(summary = "Create user", description = "Creates a new user account.")
    public boolean createUser(@RequestBody UserVo user) {
        userService.createUser(user);
        return true;
    }

    @GetMapping("/infoVo")
    @ApiOperationAuth(summary = "Get current user info", description = "Returns current user profile and permissions.")
    public ResponseType<UserVo> getUserInfo() {
        return new ResponseType<>(authService.getCurrentUserInfo());
    }

    @GetMapping("/{id}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "Get user by ID", description = "根據 ID 取得使用者資訊。")
    public ResponseType<UserVo> getUserById(@PathVariable String id) {
        return ResponseType.Success(userService.getUserById(id), "使用者查詢成功");
    }

    @GetMapping("/getAllUser")
    @RequirePermission("View")
    @ApiOperationOk(summary = "Get all users", description = "Returns all users with their roles and permissions.")
    public ResponseType<List<UserVo>> getAllUser() {
        return new ResponseType<>(0, userService.getAllUsersVo());
    }

    @PostMapping("/saveUser")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Save user with roles", description = "Updates a user and their role assignments.")
    public ResponseType<String> saveUser(@RequestBody UserVo user) {
        userService.saveUserWithRole(user);
        return new ResponseType<>(0, "User updated successfully");
    }

    @PostMapping("/{userId}/roles/rebind")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(
            summary = "Rebind user roles",
            description = "完整覆蓋式綁定使用者角色。空清單清空所有角色，null 清單拋出異常。"
    )
    public ResponseType<String> rebindUserRoles(
            @PathVariable String userId,
            @Valid @RequestBody UserRoleRebindRequest request) {
        UUID userUuid = UUID.fromString(userId);
        List<String> roleIds = request.getRoleIds() == null
                ? List.of()
                : request.getRoleIds();
        log.info("Rebinding user {} roles to {} roles", userUuid, roleIds.size());
        userService.rebindUserRoles(userUuid, roleIds);
        log.info("Rebound user {} roles successfully", userUuid);
        return ResponseType.Success("User roles rebound successfully");
    }
    
    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "Search users with pagination", description = "搜尋使用者並回傳分頁結果，支援多種查詢條件與排序")
    public ResponseType<PageResult<UserVo>> searchUsers(@Valid @RequestBody UserSearchQuery query) {
        PageResult<UserVo> result = userService.searchUsers(query);
        return new ResponseType<>(result);
    }
}
