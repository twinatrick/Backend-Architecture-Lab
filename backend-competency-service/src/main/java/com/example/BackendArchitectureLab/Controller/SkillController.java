package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Vo.Search.SkillSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.ISkillService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.CurrentUserSkillVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.SkillVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/skill")
@ApiControllerTag(name = "Skills", description = "Backend API endpoints - Skill management")
public class SkillController {
    @Autowired
    private ISkillService skillService;

    @PostMapping("/add")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Add skill", description = "Creates a new skill.")
    public ResponseType<SkillVo> addSkill(@RequestBody SkillVo skill) {
        return ResponseType.Success(skillService.addSkill(skill), "Skill added successfully");
    }

    @GetMapping("/get")
    @RequirePermission("View")
    @ApiOperationOk(summary = "Get skills", description = "Returns all skills.")
    public ResponseType<List<SkillVo>> getSkill() {
        return ResponseType.Success(skillService.getSkill(), "Skills fetched successfully");
    }

    @PostMapping("/update")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Update skill", description = "Updates an existing skill.")
    public ResponseType<String> updateSkill(@RequestBody SkillVo skill) {
        skillService.updateSkill(skill);
        return ResponseType.Success("Skill updated successfully");
    }

    @PostMapping("/delete")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Delete skill", description = "Deletes a skill.")
    public ResponseType<String> deleteSkill(@RequestBody SkillVo skill) {
        skillService.deleteSkill(skill);
        return ResponseType.Success("Skill deleted successfully");
    }

    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "搜尋技能（分頁）", description = "支援 name、description、createdBy 查詢條件，預設按 createdTime 降序排序")
    public ResponseType<PageResult<SkillVo>> searchSkills(@RequestBody SkillSearchQuery query) {
        return ResponseType.Success(skillService.searchSkills(query), "技能查詢成功");
    }

    @GetMapping("/current")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得當前使用者技能", description = "合併 USER（直接綁定）和 PROJECT（專案技能）兩個來源，每筆標記 sourceType。管理者指派技能可查看但不可透過個人 API 修改；可依權限進行綁定關聯。")
    public ResponseType<List<CurrentUserSkillVo>> getCurrentUserSkills() {
        return ResponseType.Success(skillService.getCurrentUserSkills(), "當前使用者技能查詢成功");
    }

    @PostMapping("/current/search")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "搜尋當前使用者技能（分頁）", description = "在合併後的技能列表中搜尋，支援 name、description、createdBy 查詢條件。管理者指派技能可查詢但不可透過個人 API 修改；可依權限進行綁定關聯。")
    public ResponseType<PageResult<CurrentUserSkillVo>> searchCurrentUserSkills(@RequestBody SkillSearchQuery query) {
        return ResponseType.Success(skillService.searchCurrentUserSkills(query), "當前使用者技能查詢成功");
    }
}