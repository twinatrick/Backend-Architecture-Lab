package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Dto.Vo.ResponseType;
import com.example.BackendArchitectureLab.Dto.Vo.UserJobLinkVo;
import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Service.IUserJobLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/user/bindings/job")
@ApiControllerTag(name = "User Job Bindings", description = "Backend API endpoints - User job binding self-service")
public class UserJobBindingController {

    private static final Logger log = LoggerFactory.getLogger(UserJobBindingController.class);

    @Autowired
    private IUserJobLinkService userJobLinkService;
    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

    @PostMapping("/add/{jobPostingId}")
    @ApiOperationBadRequest(summary = "綁定職缺", description = "當前使用者綁定一筆職缺。")
    public ResponseType<UserJobLinkVo> addJob(@PathVariable String jobPostingId) {
        UUID currentUserId = requireCurrentUserId();
        log.info("User {} binding job {}", currentUserId, jobPostingId);
        UserJobLinkVo result = userJobLinkService.addJobToCurrentUser(currentUserId.toString(), jobPostingId);
        return ResponseType.Success(result, "職缺綁定成功");
    }

    @DeleteMapping("/{jobPostingId}")
    @ApiOperationBadRequest(summary = "解除綁定職缺", description = "當前使用者解除綁定一筆職缺。")
    public ResponseType<String> removeJob(@PathVariable String jobPostingId) {
        UUID currentUserId = requireCurrentUserId();
        log.info("User {} unbinding job {}", currentUserId, jobPostingId);
        userJobLinkService.removeJobFromCurrentUser(currentUserId.toString(), jobPostingId);
        return ResponseType.Success("職解除綁定成功");
    }

    @GetMapping
    @ApiOperationOk(summary = "取得已綁定職缺列表", description = "取得當前使用者所有已綁定的職缺。")
    public ResponseType<List<UserJobLinkVo>> getMyJobs() {
        UUID currentUserId = requireCurrentUserId();
        List<UserJobLinkVo> result = userJobLinkService.getCurrentUserJobLinks(currentUserId.toString());
        return ResponseType.Success(result, "已綁定職缺查詢成功");
    }

    private UUID requireCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Current user not found - no authentication");
        }
        String email = auth.getName();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Current user not found - no email in authentication");
        }
        UserVo userVo = userServiceFeignClient.getUserByEmail(email);
        if (userVo == null || userVo.getId() == null) {
            throw new IllegalStateException("Current user not found - user lookup failed");
        }
        return UUID.fromString(userVo.getId());
    }
}
