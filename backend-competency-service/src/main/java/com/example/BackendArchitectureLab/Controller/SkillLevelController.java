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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/skill/level")
@ApiControllerTag(name = "Skill Levels", description = "技能等級管理相關 API")
public class SkillLevelController {
    @Autowired
    private ISkillService skillService;

    @PostMapping("/add")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增技能等級", description = "在指定技能下建立一筆等級。")
    public ResponseType<SkillLevelVo> addSkillLevel(@RequestBody SkillLevelVo skillLevelVo) {
        return ResponseType.Success(skillService.addSkillLevel(skillLevelVo), "技能等級新增成功");
    }

    @GetMapping("/get/{skillId}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得技能等級列表", description = "回傳指定技能的所有等級。")
    public ResponseType<List<SkillLevelVo>> getSkillLevels(@PathVariable String skillId) {
        return ResponseType.Success(skillService.getSkillLevels(skillId), "技能等級查詢成功");
    }

    @PostMapping("/update")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新技能等級", description = "更新一筆技能等級。")
    public ResponseType<String> updateSkillLevel(@RequestBody SkillLevelVo skillLevelVo) {
        skillService.updateSkillLevel(skillLevelVo);
        return ResponseType.Success("技能等級更新成功");
    }

    @PostMapping("/delete")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除技能等級", description = "刪除一筆技能等級。")
    public ResponseType<String> deleteSkillLevel(@RequestBody SkillLevelVo skillLevelVo) {
        skillService.deleteSkillLevel(skillLevelVo.getId());
        return ResponseType.Success("技能等級刪除成功");
    }

    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "搜尋技能等級（分頁）", description = "支援 skillId、levelValue、title、description、createdBy 查詢條件，預設按 createdTime 降序排序")
    public ResponseType<PageResult<SkillLevelVo>> searchSkillLevels(@RequestBody SkillLevelSearchQuery query) {
        return ResponseType.Success(skillService.searchSkillLevels(query), "技能等級查詢成功");
    }
}