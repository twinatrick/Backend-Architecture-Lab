package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiBody;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiPath;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiQuery;
import com.example.BackendArchitectureLab.Dto.Request.BatchIdsRequest;
import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Dto.Query.UserSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IAuthService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationAuth;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Dto.Request.UserRoleRebindRequest;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.UserVo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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


    @PostMapping
    @RequirePermission("Create")
    @ApiOperationBadRequest(summary = "新增使用者", description = "建立新的使用者帳號。")
    public boolean createUser(@ApiBody(description = "使用者資料") UserVo user) {
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
    public ResponseType<UserVo> getUserById(
            @ApiPath(name = "id", description = "使用者唯一識別碼", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid") UUID id) {
        return ResponseType.Success(userService.getUserById(id.toString()), "使用者查詢成功");
    }

    @PostMapping("/batch-query")
    @RequirePermission("View")
    @ApiOperationOk(summary = "批次依 ID 取得使用者清單", description = "傳入使用者 ID 清單（最多 100 筆），優先命中細粒度快取，未命中批次查庫並回填快取。")
    public ResponseType<BatchResultVo<UserVo>> getUsersByIds(
            @ApiBody(description = "批次 ID 請求") BatchIdsRequest request) {
        return ResponseType.Success(userService.getUsersByIds(request.getIds()), "批次使用者查詢成功");
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("View")
    @ApiOperationOk(summary = "串流分塊取得使用者清單", description = "透過 Server-Sent Events (SSE) 分塊串流推播使用者清單，支援心跳保活與非阻塞背壓。")
    public Flux<ServerSentEvent<List<UserVo>>> streamUsers(
            @ApiQuery(description = "串流分塊大小", defaultValue = "250", example = "250") int chunkSize
    ) {
        return userService.streamUsersChunked(chunkSize);
    }

    @PutMapping("/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新使用者與角色", description = "更新指定 ID 之使用者及其角色綁定。")
    public ResponseType<String> updateUser(
            @ApiPath(name = "id", description = "使用者唯一識別碼", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid") UUID id,
            @ApiBody(description = "使用者更新資料") UserVo user) {
        user.setId(id.toString());
        userService.saveUserWithRole(user);
        return new ResponseType<>(0, "使用者更新成功");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除使用者", description = "刪除指定 ID 之使用者並單向清除其角色綁定關聯。")
    public ResponseType<String> deleteUser(
            @ApiPath(name = "id", description = "使用者唯一識別碼", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid") UUID id) {
        userService.deleteUser(id.toString());
        return ResponseType.Success("使用者刪除成功");
    }

    @PostMapping("/{userId}/roles/rebind")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(
            summary = "重新綁定使用者角色",
            description = "完整覆蓋式綁定使用者角色。空清單清空所有角色，null 清單拋出異常。"
    )
    public ResponseType<String> rebindUserRoles(
            @ApiPath(name = "userId", description = "使用者唯一識別碼", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid") UUID userId,
            @ApiBody(description = "角色重新綁定請求") UserRoleRebindRequest request) {
        List<String> roleIds = request.getRoleIds() == null
                ? List.of()
                : request.getRoleIds();
        log.info("Rebinding user {} roles to {} roles", userId, roleIds.size());
        userService.rebindUserRoles(userId, roleIds);
        log.info("Rebound user {} roles successfully", userId);
        return ResponseType.Success("使用者角色重新綁定成功");
    }
    
    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "分頁搜尋使用者", description = "搜尋使用者並回傳分頁結果，支援多種查詢條件與排序")
    public ResponseType<PageResult<UserVo>> searchUsers(@ApiBody(description = "分頁搜尋條件") UserSearchQuery query) {
        PageResult<UserVo> result = userService.searchUsers(query);
        return new ResponseType<>(result);
    }
}
