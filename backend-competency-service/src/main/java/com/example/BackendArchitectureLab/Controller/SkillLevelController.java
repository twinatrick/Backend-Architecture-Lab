package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Vo.Search.SkillLevelSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.ISkillService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.SkillLevelVo;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/skill/level")
@ApiControllerTag(name = "Skill Levels", description = "Backend API endpoints - Skill level management")
public class SkillLevelController {
    @Autowired
    private ISkillService skillService;

    @PostMapping("/add")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Add skill level", description = "Creates a level under a specific skill.")
    public ResponseType<SkillLevelVo> addSkillLevel(@RequestBody SkillLevelVo skillLevelVo) {
        return ResponseType.Success(skillService.addSkillLevel(skillLevelVo), "Skill level added successfully");
    }

    @GetMapping("/get/{skillId}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "Get skill levels", description = "Returns all levels for a skill.")
    public ResponseType<List<SkillLevelVo>> getSkillLevels(@PathVariable String skillId) {
        return ResponseType.Success(skillService.getSkillLevels(skillId), "Skill levels fetched successfully");
    }

    @PostMapping("/update")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Update skill level", description = "Updates a skill level.")
    public ResponseType<String> updateSkillLevel(@RequestBody SkillLevelVo skillLevelVo) {
        skillService.updateSkillLevel(skillLevelVo);
        return ResponseType.Success("Skill level updated successfully");
    }

    @PostMapping("/delete")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Delete skill level", description = "Deletes a skill level.")
    public ResponseType<String> deleteSkillLevel(@RequestBody SkillLevelVo skillLevelVo) {
        skillService.deleteSkillLevel(skillLevelVo.getId());
        return ResponseType.Success("Skill level deleted successfully");
    }

    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "搜尋技能等級", description = "支援分頁與條件查詢的技能等級搜尋")
    @Operation(summary = "搜尋技能等級（分頁）", description = "支援 skillId、levelValue、title、description、createdBy 查詢條件，預設按 createdTime 降序排序")
    public ResponseType<PageResult<SkillLevelVo>> searchSkillLevels(@RequestBody SkillLevelSearchQuery query) {
        return ResponseType.Success(skillService.searchSkillLevels(query), "技能等級查詢成功");
    }
}