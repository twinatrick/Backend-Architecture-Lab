package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Service.IProjectCommandService;
import com.example.BackendArchitectureLab.Service.IProjectSkillService;
import com.example.BackendArchitectureLab.Vo.PersonalProjectRequest;
import com.example.BackendArchitectureLab.Vo.PersonalProjectSkillBindRequest;
import com.example.BackendArchitectureLab.Vo.PersonalProjectSkillLevelRequest;
import com.example.BackendArchitectureLab.Vo.ProjectSkillVo;
import com.example.BackendArchitectureLab.Vo.ProjectVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/project/personal")
@ApiControllerTag(name = "Personal Projects", description = "個人專案管理相關 API")
public class PersonalProjectController {
    @Autowired
    private IProjectCommandService projectCommandService;
    @Autowired
    private IProjectSkillService projectSkillService;

    @PostMapping("/add")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增個人專案", description = "新增個人專案，自動綁定當前使用者")
    public ResponseType<ProjectVo> addPersonalProject(@Valid @RequestBody PersonalProjectRequest request) {
        ProjectVo projectVo = projectCommandService.addPersonalProject(request);
        return ResponseType.Success(projectVo, "個人專案新增成功");
    }

    @PutMapping("/update/{projectId}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新個人專案", description = "修改個人專案，僅限擁有者。")
    public ResponseType<String> updatePersonalProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody PersonalProjectRequest request) {
        projectCommandService.updatePersonalProject(projectId, request);
        return ResponseType.Success("個人專案更新成功");
    }

    @DeleteMapping("/delete/{projectId}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除個人專案", description = "刪除個人專案，僅限專屬。")
    public ResponseType<String> deletePersonalProject(@PathVariable UUID projectId) {
        projectCommandService.deletePersonalProject(projectId);
        return ResponseType.Success("個人專案刪除成功");
    }

    @GetMapping("/{projectId}/skills")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得個人專案技能", description = "獲取個人專屬的專案綁定的所有技能與等級詳細資訊，會驗證當前使用者權限。")
    public ResponseType<List<ProjectSkillVo>> getPersonalProjectSkills(@PathVariable UUID projectId) {
        List<ProjectSkillVo> skills = projectSkillService.getPersonalProjectSkills(projectId);
        return ResponseType.Success(skills, "個人專案技能查詢成功");
    }

    @PostMapping("/{projectId}/skill/bind")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "綁定個人專案技能", description = "綁定技能到可操作的個人專案。管理員指定專案雖不可修改主資料，但可修改綁定關係。每個專案技能綁定只能選擇一個等級。")
    public ResponseType<String> bindPersonalProjectSkill(
            @PathVariable UUID projectId,
            @Valid @RequestBody PersonalProjectSkillBindRequest request) {
        projectSkillService.bindPersonalProjectSkill(
                projectId,
                UUID.fromString(request.getSkillId()),
                UUID.fromString(request.getSkillLevelId())
        );
        return ResponseType.Success("個人專案技能綁定成功");
    }

    @PutMapping("/{projectId}/skill/{skillId}/level")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新個人專案技能等級", description = "更新個人可操作專案中某技能的等級綁定。僅接受既有等級 ID。")
    public ResponseType<String> updatePersonalProjectSkillLevel(
            @PathVariable UUID projectId,
            @PathVariable UUID skillId,
            @Valid @RequestBody PersonalProjectSkillLevelRequest request) {
        projectSkillService.updatePersonalProjectSkillLevel(projectId, skillId, UUID.fromString(request.getSkillLevelId()));
        return ResponseType.Success("個人專案技能等級更新成功");
    }

    @DeleteMapping("/{projectId}/skill/{skillId}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "解除個人專案技能綁定", description = "解除個人可操作專案中的技能綁定。")
    public ResponseType<String> unbindPersonalProjectSkill(
            @PathVariable UUID projectId,
            @PathVariable UUID skillId) {
        projectSkillService.unbindPersonalProjectSkill(projectId, skillId);
        return ResponseType.Success("個人專案技能解除綁定成功");
    }
}