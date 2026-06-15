package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Dto.Vo.ResponseType;
import com.example.BackendArchitectureLab.Dto.Vo.UserJobLinkVo;
import com.example.BackendArchitectureLab.Service.IUserJobLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user-job-link")
@ApiControllerTag(name = "User Job Link", description = "Backend API endpoints - User job link management")
public class UserJobLinkController {

    @Autowired
    private IUserJobLinkService userJobLinkService;

    @PostMapping("/add")
    @RequirePermission({"System", "UserJobLink", "Edit"})
    @ApiOperationBadRequest(summary = "新增使用者職缺連結", description = "建立使用者與職缺的關聯。")
    public ResponseType<UserJobLinkVo> addUserJobLink(@RequestBody UserJobLinkVo userJobLinkVo) {
        return ResponseType.Success(userJobLinkService.createUserJobLink(userJobLinkVo), "使用者職缺連結新增成功");
    }

    @GetMapping("/get")
    @RequirePermission({"System", "UserJobLink", "View"})
    @ApiOperationOk(summary = "取得所有連結", description = "返回所有使用者職缺連結列表。")
    public ResponseType<List<UserJobLinkVo>> getAllUserJobLinks() {
        return ResponseType.Success(userJobLinkService.getAllUserJobLinks(), "使用者職缺連結查詢成功");
    }

    @GetMapping("/get/{id}")
    @RequirePermission({"System", "UserJobLink", "View"})
    @ApiOperationOk(summary = "取得連結詳情", description = "根據 ID 取得使用者職缺連結資訊。")
    public ResponseType<UserJobLinkVo> getUserJobLinkById(@PathVariable String id) {
        return ResponseType.Success(userJobLinkService.getUserJobLinkById(id), "使用者職缺連結查詢成功");
    }

    @GetMapping("/user/{userId}")
    @RequirePermission({"System", "UserJobLink", "View"})
    @ApiOperationOk(summary = "取得使用者所有職缺連結", description = "根據使用者 ID 取得該使用者所有職缺連結。")
    public ResponseType<List<UserJobLinkVo>> getUserJobLinksByUserId(@PathVariable String userId) {
        return ResponseType.Success(userJobLinkService.getUserJobLinksByUserId(userId), "使用者職缺連結查詢成功");
    }

    @GetMapping("/job-posting/{jobPostingId}")
    @RequirePermission({"System", "UserJobLink", "View"})
    @ApiOperationOk(summary = "取得職缺所有使用者連結", description = "根據職缺 ID 取得該職缺的所有使用者連結。")
    public ResponseType<List<UserJobLinkVo>> getUserJobLinksByJobPostingId(@PathVariable String jobPostingId) {
        return ResponseType.Success(userJobLinkService.getUserJobLinksByJobPostingId(jobPostingId), "職缺使用者連結查詢成功");
    }

    @DeleteMapping("/delete/{id}")
    @RequirePermission({"System", "UserJobLink", "Edit"})
    @ApiOperationBadRequest(summary = "刪除使用者職缺連結", description = "根據 ID 刪除使用者職缺連結。")
    public ResponseType<String> deleteUserJobLink(@PathVariable String id) {
        userJobLinkService.deleteUserJobLink(id);
        return ResponseType.Success("使用者職缺連結刪除成功");
    }
}
