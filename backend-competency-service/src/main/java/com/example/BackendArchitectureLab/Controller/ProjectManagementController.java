package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Service.IProjectService;
import com.example.BackendArchitectureLab.Service.ISkillService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.ProjectMemberSkillVo;
import com.example.BackendArchitectureLab.Vo.ProjectSkillBindRequest;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 專案管理（管理層）權限 Controller：綁定技能、檢視成員技能等管理操作。
 */
@RestController
@RequestMapping("/project")
@ApiControllerTag(name = "Project Management", description = "Backend API endpoints - Project management operations")
public class ProjectManagementController {
    @Autowired
    private IProjectService projectService;
    @Autowired
    private ISkillService skillService;

    @PostMapping("/bindSkill")
    @Deprecated
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Bind project skill", description = "Binds a skill level to a project. This operation manages binding relation only and does not modify skill content. Admin-assigned skills can still be bound by authorized users.")
    public ResponseType<String> bindProjectSkill(@RequestBody ProjectSkillBindRequest body) {
        skillService.bindProjectSkill(body.getProjectId(), body.getSkillId(), body.getSkillLevelId());
        return ResponseType.Success("Project skill bound successfully");
    }

    @GetMapping("/{projectId}/member-skills")
    @RequirePermission("EditAll")
    @ApiOperationOk(
            summary = "Get project member skills",
            description = "取得專案所有成員在此專案中的技能等級綁定，供編輯專案時回填既有資料"
    )
    public ResponseType<List<ProjectMemberSkillVo>> getProjectMemberSkills(@PathVariable UUID projectId) {
        List<ProjectMemberSkillVo> members = projectService.getProjectMemberSkills(projectId);
        return ResponseType.Success(members, "Project member skills fetched successfully");
    }
}