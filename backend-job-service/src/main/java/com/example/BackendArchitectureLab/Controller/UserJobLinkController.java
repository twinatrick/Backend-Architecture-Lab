package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiBody;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiPath;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiQuery;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Service.IUserJobLinkService;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.Search.UserJobLinkSearchQuery;
import com.example.BackendArchitectureLab.Vo.UserJobLinkVo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/user-job-link")
@RequiredArgsConstructor
@ApiControllerTag(name = "User Job Link", description = "使用者職缺連結管理相關 API")
public class UserJobLinkController {

    private final IUserJobLinkService userJobLinkService;

    @PostMapping
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增使用者職缺連結", description = "建立使用者與職缺的關聯。")
    public ResponseType<UserJobLinkVo> addUserJobLink(
            @ApiBody(description = "使用者職缺連結資料") UserJobLinkVo userJobLinkVo) {
        return ResponseType.Success(userJobLinkService.createUserJobLink(userJobLinkVo), "使用者職缺連結新增成功");
    }

    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "分頁搜尋使用者職缺連結", description = "根據條件分頁搜尋使用者職缺連結。")
    public ResponseType<PageResult<UserJobLinkVo>> searchUserJobLinks(
            @ApiBody(description = "分頁搜尋條件") UserJobLinkSearchQuery query) {
        return ResponseType.Success(userJobLinkService.searchUserJobLinks(query), "使用者職缺連結搜尋成功");
    }

    @PutMapping("/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新使用者職缺連結", description = "更新使用者職缺連結的使用者備註與 Gemini 回饋。")
    public ResponseType<UserJobLinkVo> updateUserJobLink(
            @ApiPath(description = "連結唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") String id,
            @ApiBody(description = "使用者職缺連結更新資料") UserJobLinkVo userJobLinkVo) {
        userJobLinkVo.setId(id);
        return ResponseType.Success(userJobLinkService.updateUserJobLink(userJobLinkVo), "使用者職缺連結更新成功");
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("View")
    @ApiOperationOk(summary = "串流分塊取得所有使用者職缺連結", description = "透過 Server-Sent Events (SSE) 分塊串流返回使用者職缺連結數據。")
    public Flux<ServerSentEvent<List<UserJobLinkVo>>> streamUserJobLinks(
            @ApiQuery(name = "chunkSize", defaultValue = "250", description = "串流分塊大小", example = "250") int chunkSize) {
        return userJobLinkService.streamUserJobLinksChunked(chunkSize);
    }

    @GetMapping("/{id}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得連結詳情", description = "根據 ID 取得使用者職缺連結資訊。")
    public ResponseType<UserJobLinkVo> getUserJobLinkById(
            @ApiPath(description = "連結唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") String id) {
        return ResponseType.Success(userJobLinkService.getUserJobLinkById(id), "使用者職缺連結查詢成功");
    }

    @GetMapping("/user/{userId}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得使用者所有職缺連結", description = "根據使用者 ID 取得該使用者所有職缺連結。")
    public ResponseType<List<UserJobLinkVo>> getUserJobLinksByUserId(
            @ApiPath(description = "使用者唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") String userId) {
        return ResponseType.Success(userJobLinkService.getUserJobLinksByUserId(userId), "使用者職缺連結查詢成功");
    }

    @GetMapping("/job-posting/{jobPostingId}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得職缺所有使用者連結", description = "根據職缺 ID 取得該職缺的所有使用者連結。")
    public ResponseType<List<UserJobLinkVo>> getUserJobLinksByJobPostingId(
            @ApiPath(description = "職缺唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") String jobPostingId) {
        return ResponseType.Success(userJobLinkService.getUserJobLinksByJobPostingId(jobPostingId), "職缺使用者連結查詢成功");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除使用者職缺連結", description = "根據 ID 刪除使用者職缺連結。")
    public ResponseType<String> deleteUserJobLink(
            @ApiPath(description = "連結唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") String id) {
        userJobLinkService.deleteUserJobLink(id);
        return ResponseType.Success("使用者職缺連結刪除成功");
    }
}
