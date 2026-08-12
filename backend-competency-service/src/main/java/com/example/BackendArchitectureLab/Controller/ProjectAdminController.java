package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Vo.MemberSkillBindings;
import com.example.BackendArchitectureLab.Vo.ProjectMemberSkillsRebindRequest;
import com.example.BackendArchitectureLab.Vo.ProjectSkillRebindRequest;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.UserProjectRebindRequest;
import com.example.BackendArchitectureLab.Vo.UserSkillRebindRequest;
import com.example.BackendArchitectureLab.Service.IProjectSkillService;
import com.example.BackendArchitectureLab.Service.IProjectUserBindingService;
import com.example.BackendArchitectureLab.Service.IUserProjectService;
import com.example.BackendArchitectureLab.Service.ISkillService;
import com.example.BackendArchitectureLab.Util.SkillLevelBindingMapper;
import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/project/admin/bindings")
@RequirePermission(layer = "ProjectManagement")
@ApiControllerTag(name = "Project Admin", description = "專案管理員綁定管理相關 API")
public class ProjectAdminController {

    private static final Logger log = LoggerFactory.getLogger(ProjectAdminController.class);

    @Autowired
    private IProjectSkillService projectSkillService;
    @Autowired
    private IProjectUserBindingService projectUserBindingService;
    @Autowired
    private IUserProjectService userProjectService;
    @Autowired
    private ISkillService skillService;

    @PostMapping("/user-project/rebind")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "重新綁定使用者專案", description = "以 diff 策略重新綁定所有使用者-專案關聯。")
    public ResponseType<String> rebindUserProjects(@Valid @RequestBody UserProjectRebindRequest request) {
        UUID userId = parseUuid(request.getUserId(), "userId");
        List<UUID> projectIds = request.getProjectIds() == null
                ? List.of()
                : request.getProjectIds().stream().map(id -> parseUuid(id, "projectId")).toList();
        log.info("Admin rebinding user {} to {} projects", userId, projectIds.size());
        userProjectService.rebindUserProjects(userId, projectIds);
        log.info("Admin rebound user {} projects successfully", userId);
        return ResponseType.Success("使用者專案重新綁定成功");
    }

    @PostMapping("/user-skill/rebind")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "重新綁定使用者技能", description = "以等級 diff 策略重新綁定所有使用者-技能關聯。")
    public ResponseType<String> rebindUserSkills(@Valid @RequestBody UserSkillRebindRequest request) {
        UUID userId = parseUuid(request.getUserId(), "userId");
        int bindingCount = request.getBindings() == null ? 0 : request.getBindings().size();
        log.info("Admin rebinding user {} with {} skill bindings", userId, bindingCount);
        skillService.rebindUserSkills(userId, SkillLevelBindingMapper.toSkillLevelMap(request.getBindings()));
        log.info("Admin rebound user {} skills successfully", userId);
        return ResponseType.Success("使用者技能重新綁定成功");
    }

    @PostMapping("/project-skill/rebind")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "重新綁定專案技能", description = "以等級 diff 策略重新綁定所有專案-技能關聯。")
    public ResponseType<String> rebindProjectSkills(@Valid @RequestBody ProjectSkillRebindRequest request) {
        UUID projectId = parseUuid(request.getProjectId(), "projectId");
        int bindingCount = request.getBindings() == null ? 0 : request.getBindings().size();
        log.info("Admin rebinding project {} with {} skill bindings", projectId, bindingCount);
        projectSkillService.rebindProjectSkills(projectId, SkillLevelBindingMapper.toSkillLevelMap(request.getBindings()));
        log.info("Admin rebound project {} skills successfully", projectId);
        return ResponseType.Success("專案技能重新綁定成功");
    }

    @PostMapping("/project-members-skills/rebind")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(
            summary = "重新綁定專案成員技能",
            description = "完整覆蓋式綁定專案成員技能。使用者必須已是專案成員（user_project 存在），否則拋出異常。"
    )
    public ResponseType<String> rebindProjectMemberSkills(@Valid @RequestBody ProjectMemberSkillsRebindRequest request) {
        UUID projectId = parseUuid(request.getProjectId(), "projectId");
        int memberCount = request.getMembers() == null ? 0 : request.getMembers().size();
        log.info("Admin rebinding project {} member skills for {} members", projectId, memberCount);

        Map<UUID, Map<UUID, UUID>> memberSkillsMap = new HashMap<>();
        if (request.getMembers() != null) {
            for (MemberSkillBindings member : request.getMembers()) {
                if (member == null) continue;
                UUID userId = parseUuid(member.getUserId(), "member.userId");
                Map<UUID, UUID> skillLevelMap = SkillLevelBindingMapper.toSkillLevelMap(
                        member.getSkills() == null ? List.of() : member.getSkills()
                );
                memberSkillsMap.put(userId, skillLevelMap);
            }
        }

        projectUserBindingService.rebindProjectMemberSkills(projectId, memberSkillsMap);
        log.info("Admin rebound project {} member skills successfully", projectId);
        return ResponseType.Success("專案成員技能重新綁定成功");
    }

    private static UUID parseUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + " format: " + value);
        }
    }
}
