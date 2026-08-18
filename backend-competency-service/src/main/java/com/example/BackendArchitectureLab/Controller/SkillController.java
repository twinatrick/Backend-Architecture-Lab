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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/skill")
@RequiredArgsConstructor
@ApiControllerTag(name = "Skills", description = "技能管理相關 API")
public class SkillController {
    private final ISkillService skillService;

    @PostMapping("/add")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增技能", description = "建立新的技能。")
    public ResponseType<SkillVo> addSkill(@RequestBody SkillVo skill) {
        return ResponseType.Success(skillService.addSkill(skill), "技能新增成功");
    }

    @GetMapping("/get")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得技能列表", description = "回傳所有技能。")
    public ResponseType<List<SkillVo>> getSkill() {
        return ResponseType.Success(skillService.getSkill(), "技能查詢成功");
    }

    @PostMapping("/update")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新技能", description = "更新一筆既有的技能。")
    public ResponseType<String> updateSkill(@RequestBody SkillVo skill) {
        skillService.updateSkill(skill);
        return ResponseType.Success("技能更新成功");
    }

    @PostMapping("/delete")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除技能", description = "刪除一筆技能。")
    public ResponseType<String> deleteSkill(@RequestBody SkillVo skill) {
        skillService.deleteSkill(skill);
        return ResponseType.Success("技能刪除成功");
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