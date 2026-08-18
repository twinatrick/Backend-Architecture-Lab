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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@ApiControllerTag(name = "Users", description = "使用者管理相關 API")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final IUserService userService;

    private final IAuthService authService;


    @PostMapping(value = "/create")
    @RequirePermission("Create")
    @ApiOperationBadRequest(summary = "新增使用者", description = "建立新的使用者帳號。")
    public boolean createUser(@RequestBody UserVo user) {
        userService.createUser(user);
        return true;
    }

    @GetMapping("/infoVo")
    @ApiOperationAuth(summary = "取得目前使用者資訊", description = "回傳目前使用者的個人資料與權限。")
    public ResponseType<UserVo> getUserInfo() {
        return new ResponseType<>(authService.getCurrentUserInfo());
    }

    @GetMapping("/{id}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "依 ID 取得使用者", description = "根據 ID 取得使用者資訊。")
    public ResponseType<UserVo> getUserById(@PathVariable String id) {
        return ResponseType.Success(userService.getUserById(id), "使用者查詢成功");
    }

    @GetMapping("/getAllUser")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得所有使用者", description = "回傳所有使用者及其角色與權限。")
    public ResponseType<List<UserVo>> getAllUser() {
        return new ResponseType<>(0, userService.getAllUsersVo());
    }

    @PostMapping("/saveUser")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "儲存使用者與角色", description = "更新使用者及其角色綁定。")
    public ResponseType<String> saveUser(@RequestBody UserVo user) {
        userService.saveUserWithRole(user);
        return new ResponseType<>(0, "使用者更新成功");
    }

    @PostMapping("/{userId}/roles/rebind")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(
            summary = "重新綁定使用者角色",
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
        return ResponseType.Success("使用者角色重新綁定成功");
    }
    
    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "分頁搜尋使用者", description = "搜尋使用者並回傳分頁結果，支援多種查詢條件與排序")
    public ResponseType<PageResult<UserVo>> searchUsers(@Valid @RequestBody UserSearchQuery query) {
        PageResult<UserVo> result = userService.searchUsers(query);
        return new ResponseType<>(result);
    }
}
