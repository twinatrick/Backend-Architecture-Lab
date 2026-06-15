package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillLevelSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.ISkillService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Dto.Vo.CurrentUserSkillVo;
import com.example.BackendArchitectureLab.Dto.Vo.PersonalSkillLevelRequest;
import com.example.BackendArchitectureLab.Dto.Vo.PersonalSkillRequest;
import com.example.BackendArchitectureLab.Dto.Vo.ResponseType;
import com.example.BackendArchitectureLab.Dto.Vo.SkillLevelVo;
import com.example.BackendArchitectureLab.Dto.Vo.SkillVo;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/skill")
@ApiControllerTag(name = "Skills", description = "Backend API endpoints - Skill management")
public class SkillController {
    @Autowired
    private ISkillService skillService;

    @PostMapping("/add")
    @RequirePermission({"System", "Skill", "Edit"})
    @ApiOperationBadRequest(summary = "Add skill", description = "Creates a new skill.")
    public ResponseType<SkillVo> addSkill(@RequestBody SkillVo skill) {
        return ResponseType.Success(skillService.addSkill(skill), "Skill added successfully");
    }

    @GetMapping("/get")
    @RequirePermission({"System", "Skill", "View"})
    @ApiOperationOk(summary = "Get skills", description = "Returns all skills.")
    public ResponseType<List<SkillVo>> getSkill() {
        return ResponseType.Success(skillService.getSkill(), "Skills fetched successfully");
    }

    @PostMapping("/update")
    @RequirePermission({"System", "Skill", "Edit"})
    @ApiOperationBadRequest(summary = "Update skill", description = "Updates an existing skill.")
    public ResponseType<String> updateSkill(@RequestBody SkillVo skill) {
        skillService.updateSkill(skill);
        return ResponseType.Success("Skill updated successfully");
    }

    @PostMapping("/delete")
    @RequirePermission({"System", "Skill", "Edit"})
    @ApiOperationBadRequest(summary = "Delete skill", description = "Deletes a skill.")
    public ResponseType<String> deleteSkill(@RequestBody SkillVo skill) {
        skillService.deleteSkill(skill);
        return ResponseType.Success("Skill deleted successfully");
    }

    @PostMapping("/level/add")
    @RequirePermission({"System", "SkillLevel", "Edit"})
    @ApiOperationBadRequest(summary = "Add skill level", description = "Creates a level under a specific skill.")
    public ResponseType<SkillLevelVo> addSkillLevel(@RequestBody SkillLevelVo skillLevelVo) {
        return ResponseType.Success(skillService.addSkillLevel(skillLevelVo), "Skill level added successfully");
    }

    @GetMapping("/level/get/{skillId}")
    @RequirePermission({"System", "SkillLevel", "View"})
    @ApiOperationOk(summary = "Get skill levels", description = "Returns all levels for a skill.")
    public ResponseType<List<SkillLevelVo>> getSkillLevels(@PathVariable String skillId) {
        return ResponseType.Success(skillService.getSkillLevels(skillId), "Skill levels fetched successfully");
    }

    @PostMapping("/level/update")
    @RequirePermission({"System", "SkillLevel", "Edit"})
    @ApiOperationBadRequest(summary = "Update skill level", description = "Updates a skill level.")
    public ResponseType<String> updateSkillLevel(@RequestBody SkillLevelVo skillLevelVo) {
        skillService.updateSkillLevel(skillLevelVo);
        return ResponseType.Success("Skill level updated successfully");
    }

    @PostMapping("/level/delete")
    @RequirePermission({"System", "SkillLevel", "Edit"})
    @ApiOperationBadRequest(summary = "Delete skill level", description = "Deletes a skill level.")
    public ResponseType<String> deleteSkillLevel(@RequestBody SkillLevelVo skillLevelVo) {
        skillService.deleteSkillLevel(skillLevelVo.getId());
        return ResponseType.Success("Skill level deleted successfully");
    }
    
    @PostMapping("/search")
    @RequirePermission({"System", "Skill", "View"})
    @ApiOperationBadRequest(summary = "搜尋技能", description = "支援分頁與條件查詢的技能搜尋")
    @Operation(summary = "搜尋技能（分頁）", description = "支援 name、description、createdBy 查詢條件，預設按 createdTime 降序排序")
    public ResponseType<PageResult<SkillVo>> searchSkills(@RequestBody SkillSearchQuery query) {
        return ResponseType.Success(skillService.searchSkills(query), "技能查詢成功");
    }
    
    @GetMapping("/current")
    @RequirePermission({"System", "Skill", "View"})
    @ApiOperationOk(summary = "取得當前使用者技能", description = "返回當前使用者所有技能（合併 USER 直接綁定與 PROJECT 專案技能）。管理者指派技能可查看但視為唯讀。")
    @Operation(summary = "取得當前使用者技能", description = "合併 USER（直接綁定）和 PROJECT（專案技能）兩個來源，每筆標記 sourceType。管理者指派技能可查看但不可透過個人 API 修改；可依權限進行綁定關聯。")
    public ResponseType<List<CurrentUserSkillVo>> getCurrentUserSkills() {
        return ResponseType.Success(skillService.getCurrentUserSkills(), "當前使用者技能查詢成功");
    }
    
    @PostMapping("/current/search")
    @RequirePermission({"System", "Skill", "View"})
    @ApiOperationBadRequest(summary = "搜尋當前使用者技能", description = "支援分頁與條件查詢的當前使用者技能搜尋。管理者指派技能可查詢但視為唯讀。")
    @Operation(summary = "搜尋當前使用者技能（分頁）", description = "在合併後的技能列表中搜尋，支援 name、description、createdBy 查詢條件。管理者指派技能可查詢但不可透過個人 API 修改；可依權限進行綁定關聯。")
    public ResponseType<PageResult<CurrentUserSkillVo>> searchCurrentUserSkills(@RequestBody SkillSearchQuery query) {
        return ResponseType.Success(skillService.searchCurrentUserSkills(query), "當前使用者技能查詢成功");
    }
    
    @PostMapping("/level/search")
    @RequirePermission({"System", "SkillLevel", "View"})
    @ApiOperationBadRequest(summary = "搜尋技能等級", description = "支援分頁與條件查詢的技能等級搜尋")
    @Operation(summary = "搜尋技能等級（分頁）", description = "支援 skillId、levelValue、title、description、createdBy 查詢條件，預設按 createdTime 降序排序")
    public ResponseType<PageResult<SkillLevelVo>> searchSkillLevels(@RequestBody SkillLevelSearchQuery query) {
        return ResponseType.Success(skillService.searchSkillLevels(query), "技能等級查詢成功");
    }
    
    @PostMapping("/personal/add")
    @RequirePermission({"System", "PersonalSkill", "Edit"})
    @ApiOperationBadRequest(summary = "新增個人技能", description = "建立技能並自動綁定當前使用者。可使用既有 skillLevelId 或手動填寫 skillLevelValue/skillLevelTitle/skillLevelDescription 建立等級。")
    @Operation(summary = "新增個人技能", description = "一般使用者新增技能，會自動綁定到當前登入使用者。若未提供 skillLevelId，可手動填寫等級值、標題與描述建立第一個技能等級。")
    public ResponseType<SkillVo> addPersonalSkill(@RequestBody PersonalSkillRequest request) {
        return ResponseType.Success(skillService.addPersonalSkill(request), "個人技能新增成功");
    }
    
    @PostMapping("/personal/update")
    @RequirePermission({"System", "PersonalSkill", "Edit"})
    @ApiOperationBadRequest(summary = "修改個人技能", description = "修改技能主資料（僅限擁有者）。管理者指派技能為唯讀，不能透過此 API 修改內容。")
    @Operation(summary = "修改個人技能", description = "只有技能的擁有者可以修改技能主資料。管理者指派給使用者的技能視為唯讀，不可透過個人修改 API 變更 name/description 等內容。")
    public ResponseType<String> updatePersonalSkill(@RequestParam String skillId, @RequestBody PersonalSkillRequest request) {
        skillService.updatePersonalSkill(UUID.fromString(skillId), request);
        return ResponseType.Success("個人技能修改成功");
    }

    @PostMapping("/personal/update-level")
    @RequirePermission({"System", "PersonalSkill", "Edit"})
    @ApiOperationBadRequest(summary = "修改個人技能綁定等級", description = "僅修改當前使用者與技能的等級綁定。管理者指派技能雖不可改主資料，但可透過此 API 修改綁定。僅接受既有 skillLevelId。")
    @Operation(summary = "修改個人技能綁定等級", description = "更新 user-skill 關聯上的 skill level，不會修改技能主資料。")
    public ResponseType<String> updatePersonalSkillLevel(@RequestParam String skillId, @RequestBody PersonalSkillLevelRequest request) {
        skillService.updatePersonalSkillLevel(UUID.fromString(skillId), UUID.fromString(request.getSkillLevelId()));
        return ResponseType.Success("個人技能綁定等級修改成功");
    }
    
    @PostMapping("/personal/delete")
    @RequirePermission({"System", "PersonalSkill", "Edit"})
    @ApiOperationBadRequest(summary = "解除個人技能綁定", description = "解除當前使用者與技能的綁定關係。此操作不會刪除技能主資料與技能等級。")
    @Operation(summary = "解除個人技能綁定", description = "移除 user-skill 綁定。管理者指派技能雖不可修改主資料，仍可解除個人綁定。")
    public ResponseType<String> deletePersonalSkill(@RequestParam String skillId) {
        skillService.deletePersonalSkill(UUID.fromString(skillId));
        return ResponseType.Success("個人技能綁定解除成功");
    }
}
