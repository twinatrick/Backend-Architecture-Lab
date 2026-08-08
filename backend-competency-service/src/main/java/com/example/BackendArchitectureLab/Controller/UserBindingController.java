package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.SkillBindingsRebindRequest;
import com.example.BackendArchitectureLab.Service.IProjectService;
import com.example.BackendArchitectureLab.Service.ISkillService;
import com.example.BackendArchitectureLab.Util.SkillLevelBindingMapper;
import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/user/bindings")
@RequirePermission(layer = "ProjectManagement")
@ApiControllerTag(name = "User Bindings", description = "使用者綁定自助服務相關 API")
public class UserBindingController {

    private static final Logger log = LoggerFactory.getLogger(UserBindingController.class);

    @Autowired
    private ISkillService skillService;
    @Autowired
    private IProjectService projectService;
    @Autowired
    private SecurityUtil securityUtil;

    @PostMapping("/skill/rebind")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "重新綁定當前使用者技能", description = "以 diff 策略重新綁定當前使用者的技能等級關聯。")
    public ResponseType<String> rebindCurrentUserSkills(@Valid @RequestBody SkillBindingsRebindRequest request) {
        UUID currentUserId = securityUtil.requireCurrentUserId();
        int bindingCount = request.getBindings() == null ? 0 : request.getBindings().size();
        log.info("User {} rebinding own skills with {} bindings", currentUserId, bindingCount);
        skillService.rebindUserSkills(currentUserId, SkillLevelBindingMapper.toSkillLevelMap(request.getBindings()));
        log.info("User {} rebound own skills successfully", currentUserId);
        return ResponseType.Success("當前使用者技能重新綁定成功");
    }

    @PostMapping("/project/{projectId}/skill/rebind")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "重新綁定當前使用者專案技能", description = "以 diff 策略重新綁定可管理專案的技能關聯。")
    public ResponseType<String> rebindCurrentUserProjectSkills(
            @PathVariable UUID projectId,
            @Valid @RequestBody SkillBindingsRebindRequest request) {
        int bindingCount = request.getBindings() == null ? 0 : request.getBindings().size();
        log.info("User {} rebinding project {} with {} skill bindings", securityUtil.requireCurrentUserId(), projectId, bindingCount);
        projectService.rebindPersonalProjectSkills(projectId, SkillLevelBindingMapper.toSkillLevelMap(request.getBindings()));
        log.info("User {} rebound project {} skills successfully", securityUtil.requireCurrentUserId(), projectId);
        return ResponseType.Success("當前使用者專案技能重新綁定成功");
    }

}
