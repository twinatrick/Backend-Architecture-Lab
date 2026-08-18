package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Vo.Search.ProjectSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IProjectCommandService;
import com.example.BackendArchitectureLab.Service.IProjectQueryService;
import com.example.BackendArchitectureLab.Service.IProjectSkillService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.ProjectSkillVo;
import com.example.BackendArchitectureLab.Vo.ProjectVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
@ApiControllerTag(name = "Projects", description = "專案管理相關 API")
public class ProjectController {
    private final IProjectCommandService projectCommandService;
    private final IProjectQueryService projectQueryService;
    private final IProjectSkillService projectSkillService;

    @PostMapping("/add")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增專案", description = "建立新的專案。")
    public ResponseType<ProjectVo> addProject(@RequestBody ProjectVo project) {
        return ResponseType.Success(projectCommandService.addProject(project), "專案新增成功");
    }

    @GetMapping("/get")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得專案列表", description = "回傳所有專案。")
    public ResponseType<List<ProjectVo>> getProject() {
        return ResponseType.Success(projectQueryService.getProject(), "專案查詢成功");
    }

    @PostMapping("/update")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新專案", description = "更新一筆既有的專案。")
    public ResponseType<String> updateProject(@RequestBody ProjectVo project) {
        projectCommandService.updateProject(project);
        return ResponseType.Success("專案更新成功");
    }

    @PostMapping("/delete")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除專案", description = "刪除一筆專案。")
    public ResponseType<String> deleteProject(@RequestBody ProjectVo project) {
        projectCommandService.deleteProject(project);
        return ResponseType.Success("專案刪除成功");
    }

    @GetMapping("/{projectId}/skills")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得專案技能", description = "獲取指定專案綁定的所有技能與等級詳細資訊")
    public ResponseType<List<ProjectSkillVo>> getProjectSkills(@PathVariable UUID projectId) {
        List<ProjectSkillVo> skills = projectSkillService.getProjectSkills(projectId);
        return ResponseType.Success(skills, "專案技能查詢成功");
    }

    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "分頁搜尋專案", description = "搜尋專案並回傳分頁結果，支援多種查詢條件與排序")
    public ResponseType<PageResult<ProjectVo>> searchProjects(@Valid @RequestBody ProjectSearchQuery query) {
        PageResult<ProjectVo> result = projectQueryService.searchProjects(query);
        return ResponseType.Success(result, "專案查詢成功");
    }

    @GetMapping("/current")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得當前使用者專案", description = "回傳當前使用者所屬的所有專案")
    public ResponseType<List<ProjectVo>> getCurrentUserProjects() {
        List<ProjectVo> projects = projectQueryService.getCurrentUserProjects();
        return ResponseType.Success(projects, "當前使用者專案查詢成功");
    }

    @PostMapping("/current/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "分頁搜尋當前使用者專案", description = "搜尋當前使用者的專案並回傳分頁結果，支援多種查詢條件與排序")
    public ResponseType<PageResult<ProjectVo>> searchCurrentUserProjects(@Valid @RequestBody ProjectSearchQuery query) {
        PageResult<ProjectVo> result = projectQueryService.searchCurrentUserProjects(query);
        return ResponseType.Success(result, "當前使用者專案查詢成功");
    }
}