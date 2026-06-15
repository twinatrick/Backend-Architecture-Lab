package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Dto.Vo.Search.ProjectSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Dto.Vo.PersonalProjectSkillBindRequest;
import com.example.BackendArchitectureLab.Dto.Vo.PersonalProjectSkillLevelRequest;
import com.example.BackendArchitectureLab.Dto.Vo.PersonalProjectRequest;
import com.example.BackendArchitectureLab.Service.IProjectService;
import com.example.BackendArchitectureLab.Feign.SkillServiceFeignClient;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Dto.Vo.ProjectMemberSkillVo;
import com.example.BackendArchitectureLab.Dto.Vo.ProjectSkillBindRequest;
import com.example.BackendArchitectureLab.Dto.Vo.ProjectSkillVo;
import com.example.BackendArchitectureLab.Dto.Vo.ProjectVo;
import com.example.BackendArchitectureLab.Dto.Vo.ResponseType;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/project")
@ApiControllerTag(name = "Projects", description = "Backend API endpoints - Project management")
public class ProjectController {
    @Autowired
    private IProjectService projectService;
    @Autowired
    private SkillServiceFeignClient skillServiceFeignClient;

    @PostMapping("/add")
    @RequirePermission({"System", "Project", "Edit"})
    @ApiOperationBadRequest(summary = "Add project", description = "Creates a new project.")
    public ResponseType<ProjectVo> addProject(@RequestBody ProjectVo project) {
        return ResponseType.Success(projectService.addProject(project), "Project added successfully");
    }

    @GetMapping("/get")
    @RequirePermission({"System", "Project", "View"})
    @ApiOperationOk(summary = "Get projects", description = "Returns all projects.")
    public ResponseType<List<ProjectVo>> getProject() {
        return ResponseType.Success(projectService.getProject(), "Projects fetched successfully");
    }

    @PostMapping("/update")
    @RequirePermission({"System", "Project", "Edit"})
    @ApiOperationBadRequest(summary = "Update project", description = "Updates an existing project.")
    public ResponseType<String> updateProject(@RequestBody ProjectVo project) {
        projectService.updateProject(project);
        return ResponseType.Success("Project updated successfully");
    }

    @PostMapping("/delete")
    @RequirePermission({"System", "Project", "Edit"})
    @ApiOperationBadRequest(summary = "Delete project", description = "Deletes a project.")
    public ResponseType<String> deleteProject(@RequestBody ProjectVo project) {
        projectService.deleteProject(project);
        return ResponseType.Success("Project deleted successfully");
    }

    @PostMapping("/bindSkill")
    @Deprecated
    @RequirePermission({"System", "ProjectManagement", "Edit"})
    @ApiOperationBadRequest(summary = "Bind project skill", description = "Binds a skill level to a project. This operation manages binding relation only and does not modify skill content. Admin-assigned skills can still be bound by authorized users.")
    public ResponseType<String> bindProjectSkill(@RequestBody ProjectSkillBindRequest body) {
        skillServiceFeignClient.bindProjectSkill(body.getProjectId(), body.getSkillId(), body.getSkillLevelId());
        return ResponseType.Success("Project skill bound successfully");
    }
    
    @GetMapping("/{projectId}/skills")
    @RequirePermission({"System", "Project", "View"})
    @ApiOperationOk(summary = "Get project skills", description = "獲取指定專案綁定的所有技能與等級詳細資訊")
    public ResponseType<List<ProjectSkillVo>> getProjectSkills(@PathVariable UUID projectId) {
        List<ProjectSkillVo> skills = projectService.getProjectSkills(projectId);
        return ResponseType.Success(skills, "Project skills fetched successfully");
    }

    @GetMapping("/{projectId}/member-skills")
    @RequirePermission({"System", "ProjectManagement", "EditAll"})
    @ApiOperationOk(
            summary = "Get project member skills",
            description = "取得專案所有成員在此專案中的技能等級綁定，供編輯專案時回填既有資料"
    )
    public ResponseType<List<ProjectMemberSkillVo>> getProjectMemberSkills(@PathVariable UUID projectId) {
        List<ProjectMemberSkillVo> members = projectService.getProjectMemberSkills(projectId);
        return ResponseType.Success(members, "Project member skills fetched successfully");
    }
    
    @PostMapping("/search")
    @RequirePermission({"System", "Project", "View"})
    @ApiOperationOk(summary = "Search projects with pagination", description = "搜尋專案並回傳分頁結果，支援多種查詢條件與排序")
    public ResponseType<PageResult<ProjectVo>> searchProjects(@Valid @RequestBody ProjectSearchQuery query) {
        PageResult<ProjectVo> result = projectService.searchProjects(query);
        return ResponseType.Success(result, "Projects fetched successfully");
    }
    
    @GetMapping("/current")
    @RequirePermission({"System", "Project", "View"})
    @ApiOperationOk(summary = "Get current user projects", description = "回傳當前使用者所屬的所有專案")
    public ResponseType<List<ProjectVo>> getCurrentUserProjects() {
        List<ProjectVo> projects = projectService.getCurrentUserProjects();
        return ResponseType.Success(projects, "Current user projects fetched successfully");
    }
    
    @PostMapping("/current/search")
    @RequirePermission({"System", "Project", "View"})
    @ApiOperationOk(summary = "Search current user projects with pagination", description = "搜尋當前使用者的專案並回傳分頁結果，支援多種查詢條件與排序")
    public ResponseType<PageResult<ProjectVo>> searchCurrentUserProjects(@Valid @RequestBody ProjectSearchQuery query) {
        PageResult<ProjectVo> result = projectService.searchCurrentUserProjects(query);
        return ResponseType.Success(result, "Current user projects fetched successfully");
    }
    
    @PostMapping("/personal/add")
    @RequirePermission({"System", "Project", "PersonalEdit"})
    @ApiOperationBadRequest(summary = "Add personal project", description = "新增個人專案，自動綁定當前使用者")
    public ResponseType<ProjectVo> addPersonalProject(@Valid @RequestBody PersonalProjectRequest request) {
        ProjectVo projectVo = projectService.addPersonalProject(request);
        return ResponseType.Success(projectVo, "Personal project added successfully");
    }
    
    @PutMapping("/personal/update/{projectId}")
    @RequirePermission({"System", "Project", "PersonalEdit"})
    @ApiOperationBadRequest(summary = "Update personal project", description = "修改個人專案，僅限擁有者")
    public ResponseType<String> updatePersonalProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody PersonalProjectRequest request) {
        projectService.updatePersonalProject(projectId, request);
        return ResponseType.Success("Personal project updated successfully");
    }
    
    @DeleteMapping("/personal/delete/{projectId}")
    @RequirePermission({"System", "Project", "PersonalEdit"})
    @ApiOperationBadRequest(summary = "Delete personal project", description = "刪除個人專案，僅限擁有者")
    public ResponseType<String> deletePersonalProject(@PathVariable UUID projectId) {
        projectService.deletePersonalProject(projectId);
        return ResponseType.Success("Personal project deleted successfully");
    }

    @GetMapping("/personal/{projectId}/skills")
    @RequirePermission({"System", "Project", "PersonalEdit"})
    @ApiOperationOk(summary = "Get personal project skills", description = "獲取個人專屬的專案綁定的所有技能與等級詳細資訊，會驗證當前使用者權限")
    public ResponseType<List<ProjectSkillVo>> getPersonalProjectSkills(@PathVariable UUID projectId) {
        List<ProjectSkillVo> skills = projectService.getPersonalProjectSkills(projectId);
        return ResponseType.Success(skills, "Personal project skills fetched successfully");
    }

    @PostMapping("/personal/{projectId}/skill/bind")
    @RequirePermission({"System", "Project", "PersonalEdit"})
    @ApiOperationBadRequest(summary = "Bind personal project skill", description = "綁定技能到可操作的個人專案。管理員指定專案雖不可修改主資料，但可修改綁定關係。每個專案技能綁定只能選擇一個等級。")
    public ResponseType<String> bindPersonalProjectSkill(
            @PathVariable UUID projectId,
            @Valid @RequestBody PersonalProjectSkillBindRequest request) {
        projectService.bindPersonalProjectSkill(
                projectId,
                UUID.fromString(request.getSkillId()),
                UUID.fromString(request.getSkillLevelId())
        );
        return ResponseType.Success("Personal project skill bound successfully");
    }

    @PutMapping("/personal/{projectId}/skill/{skillId}/level")
    @RequirePermission({"System", "Project", "PersonalEdit"})
    @ApiOperationBadRequest(summary = "Update personal project skill level", description = "更新個人可操作專案中某技能的等級綁定。僅接受既有等級 ID。")
    public ResponseType<String> updatePersonalProjectSkillLevel(
            @PathVariable UUID projectId,
            @PathVariable UUID skillId,
            @Valid @RequestBody PersonalProjectSkillLevelRequest request) {
        projectService.updatePersonalProjectSkillLevel(projectId, skillId, UUID.fromString(request.getSkillLevelId()));
        return ResponseType.Success("Personal project skill level updated successfully");
    }

    @DeleteMapping("/personal/{projectId}/skill/{skillId}")
    @RequirePermission({"System", "Project", "PersonalEdit"})
    @ApiOperationBadRequest(summary = "Unbind personal project skill", description = "解除個人可操作專案中的技能綁定。")
    public ResponseType<String> unbindPersonalProjectSkill(
            @PathVariable UUID projectId,
            @PathVariable UUID skillId) {
        projectService.unbindPersonalProjectSkill(projectId, skillId);
        return ResponseType.Success("Personal project skill unbound successfully");
    }
}
