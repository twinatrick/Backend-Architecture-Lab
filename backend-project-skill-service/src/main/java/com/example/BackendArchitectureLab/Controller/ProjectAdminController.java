package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Dto.Vo.MemberSkillBindings;
import com.example.BackendArchitectureLab.Dto.Vo.ProjectMemberSkillsRebindRequest;
import com.example.BackendArchitectureLab.Dto.Vo.ProjectSkillRebindRequest;
import com.example.BackendArchitectureLab.Dto.Vo.ResponseType;
import com.example.BackendArchitectureLab.Dto.Vo.UserProjectRebindRequest;
import com.example.BackendArchitectureLab.Dto.Vo.UserSkillRebindRequest;
import com.example.BackendArchitectureLab.Service.IProjectService;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Feign.SkillServiceFeignClient;
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
@RequirePermission({"System", "ProjectManagement", "EditAll"})
@ApiControllerTag(name = "Project Admin", description = "Backend API endpoints - Project admin binding management")
public class ProjectAdminController {

    private static final Logger log = LoggerFactory.getLogger(ProjectAdminController.class);

    @Autowired
    private UserServiceFeignClient userServiceFeignClient;
    @Autowired
    private SkillServiceFeignClient skillServiceFeignClient;
    @Autowired
    private IProjectService projectService;

    @PostMapping("/user-project/rebind")
    @ApiOperationBadRequest(summary = "Rebind user projects", description = "Rebind all user-project relations with diff strategy")
    public ResponseType<String> rebindUserProjects(@Valid @RequestBody UserProjectRebindRequest request) {
        UUID userId = parseUuid(request.getUserId(), "userId");
        List<UUID> projectIds = request.getProjectIds() == null
                ? List.of()
                : request.getProjectIds().stream().map(id -> parseUuid(id, "projectId")).toList();
        log.info("Admin rebinding user {} to {} projects", userId, projectIds.size());
        userServiceFeignClient.rebindUserProjects(userId, projectIds);
        log.info("Admin rebound user {} projects successfully", userId);
        return ResponseType.Success("User projects rebound successfully");
    }

    @PostMapping("/user-skill/rebind")
    @ApiOperationBadRequest(summary = "Rebind user skills", description = "Rebind all user-skill relations with level diff strategy")
    public ResponseType<String> rebindUserSkills(@Valid @RequestBody UserSkillRebindRequest request) {
        UUID userId = parseUuid(request.getUserId(), "userId");
        int bindingCount = request.getBindings() == null ? 0 : request.getBindings().size();
        log.info("Admin rebinding user {} with {} skill bindings", userId, bindingCount);
        skillServiceFeignClient.rebindUserSkills(userId, SkillLevelBindingMapper.toSkillLevelMap(request.getBindings()));
        log.info("Admin rebound user {} skills successfully", userId);
        return ResponseType.Success("User skills rebound successfully");
    }

    @PostMapping("/project-skill/rebind")
    @ApiOperationBadRequest(summary = "Rebind project skills", description = "Rebind all project-skill relations with level diff strategy")
    public ResponseType<String> rebindProjectSkills(@Valid @RequestBody ProjectSkillRebindRequest request) {
        UUID projectId = parseUuid(request.getProjectId(), "projectId");
        int bindingCount = request.getBindings() == null ? 0 : request.getBindings().size();
        log.info("Admin rebinding project {} with {} skill bindings", projectId, bindingCount);
        projectService.rebindProjectSkills(projectId, SkillLevelBindingMapper.toSkillLevelMap(request.getBindings()));
        log.info("Admin rebound project {} skills successfully", projectId);
        return ResponseType.Success("Project skills rebound successfully");
    }

    @PostMapping("/project-members-skills/rebind")
    @ApiOperationBadRequest(
            summary = "Rebind project member skills",
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

        projectService.rebindProjectMemberSkills(projectId, memberSkillsMap);
        log.info("Admin rebound project {} member skills successfully", projectId);
        return ResponseType.Success("Project member skills rebound successfully");
    }

    private static UUID parseUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + " format: " + value);
        }
    }
}
