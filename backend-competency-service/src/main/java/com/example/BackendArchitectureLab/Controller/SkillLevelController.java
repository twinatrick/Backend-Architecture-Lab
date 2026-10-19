package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiBody;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiPath;
import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Dto.Query.SkillLevelSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Dto.Request.BatchIdsRequest;
import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Service.ISkillService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.SkillLevelVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/skill/level")
@RequiredArgsConstructor
@ApiControllerTag(name = "Skill Levels", description = "技能等級管理相關 API")
public class SkillLevelController {
    private final ISkillService skillService;

    @GetMapping("/{id}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得技能等級詳情", description = "根據 ID 取得指定技能等級資訊。")
    public ResponseType<SkillLevelVo> getSkillLevelById(
            @ApiPath(description = "技能等級唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") UUID id) {
        return ResponseType.Success(skillService.getSkillLevelById(id), "技能等級查詢成功");
    }

    @PostMapping("/batch-query")
    @RequirePermission("View")
    @ApiOperationOk(summary = "批次依 ID 取得技能等級清單", description = "傳入技能等級 ID 清單（最多 100 筆），優先命中細粒度快取，未命中批次查庫並回填快取。")
    public ResponseType<BatchResultVo<SkillLevelVo>> getSkillLevelsByIds(
            @ApiBody(description = "批次技能等級 ID 請求") BatchIdsRequest request) {
        return ResponseType.Success(skillService.getSkillLevelsByIds(request.getIds()), "批次技能等級查詢成功");
    }

    @PostMapping
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增技能等級", description = "在指定技能下建立一筆等級。")
    public ResponseType<SkillLevelVo> addSkillLevel(
            @ApiBody(description = "技能等級資料") SkillLevelVo skillLevelVo) {
        return ResponseType.Success(skillService.addSkillLevel(skillLevelVo), "技能等級新增成功");
    }

    @GetMapping("/by-skill/{skillId}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得技能等級列表", description = "回傳指定技能的所有等級。")
    public ResponseType<List<SkillLevelVo>> getSkillLevels(
            @ApiPath(description = "技能唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") String skillId) {
        return ResponseType.Success(skillService.getSkillLevels(skillId), "技能等級查詢成功");
    }

    @PutMapping("/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新技能等級", description = "更新一筆技能等級。")
    public ResponseType<String> updateSkillLevel(
            @ApiPath(description = "技能等級唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") String id,
            @ApiBody(description = "技能等級更新資料") SkillLevelVo skillLevelVo) {
        skillLevelVo.setId(id);
        skillService.updateSkillLevel(skillLevelVo);
        return ResponseType.Success("技能等級更新成功");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除技能等級", description = "刪除一筆技能等級。")
    public ResponseType<String> deleteSkillLevel(
            @ApiPath(description = "技能等級唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") String id) {
        skillService.deleteSkillLevel(id);
        return ResponseType.Success("技能等級刪除成功");
    }

    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "搜尋技能等級（分頁）", description = "支援 skillId、levelValue、title、description、createdBy 查詢條件，預設按 createdTime 降序排序")
    public ResponseType<PageResult<SkillLevelVo>> searchSkillLevels(
            @ApiBody(description = "分頁搜尋條件") SkillLevelSearchQuery query) {
        return ResponseType.Success(skillService.searchSkillLevels(query), "技能等級查詢成功");
    }
}