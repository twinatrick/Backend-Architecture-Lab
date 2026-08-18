package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Service.IProjectUserBindingService;
import com.example.BackendArchitectureLab.Service.ISkillService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.ProjectMemberSkillVo;
import com.example.BackendArchitectureLab.Vo.ProjectSkillBindRequest;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 專案管理（管理層）權限 Controller：綁定技能、檢視成員技能等管理操作。
 */
@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
@ApiControllerTag(name = "Project Management", description = "專案管理（管理層）相關 API")
public class ProjectManagementController {
    private final IProjectUserBindingService projectUserBindingService;
    private final ISkillService skillService;

    @PostMapping("/bindSkill")
    @Deprecated
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "綁定專案技能", description = "將技能等級綁定至專案。此操作僅管理綁定關係，不修改技能內容。管理者指派的技能仍可被授權使用者綁定。")
    public ResponseType<String> bindProjectSkill(@RequestBody ProjectSkillBindRequest body) {
        skillService.bindProjectSkill(body.getProjectId(), body.getSkillId(), body.getSkillLevelId());
        return ResponseType.Success("專案技能綁定成功");
    }

    @GetMapping("/{projectId}/member-skills")
    @RequirePermission("View")
    @ApiOperationOk(
            summary = "取得專案成員技能",
            description = "取得專案所有成員在此專案中的技能等級綁定，供編輯專案時回填既有資料"
    )
    public ResponseType<List<ProjectMemberSkillVo>> getProjectMemberSkills(@PathVariable UUID projectId) {
        List<ProjectMemberSkillVo> members = projectUserBindingService.getProjectMemberSkills(projectId);
        return ResponseType.Success(members, "專案成員技能查詢成功");
    }
}