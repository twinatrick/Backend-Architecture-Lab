package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiBody;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiPath;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiQuery;
import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Dto.Query.SkillSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.ISkillService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.CurrentUserSkillVo;
import com.example.BackendArchitectureLab.Dto.Request.BatchIdsRequest;
import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.SkillVo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/skill")
@RequiredArgsConstructor
@ApiControllerTag(name = "Skills", description = "技能管理相關 API")
public class SkillController {
    private final ISkillService skillService;

    @PostMapping
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增技能", description = "建立新的技能。")
    public ResponseType<SkillVo> addSkill(
            @ApiBody(description = "技能資料") SkillVo skill) {
        return ResponseType.Success(skillService.addSkill(skill), "技能新增成功");
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("View")
    @ApiOperationOk(summary = "技能分塊串流", description = "以 SSE 響應式分塊串流取得技能列表，避免萬筆級全表查詢與記憶體過載")
    public Flux<ServerSentEvent<List<SkillVo>>> streamSkills(
            @ApiQuery(name = "chunkSize", defaultValue = "250", description = "串流分塊大小", example = "250") int chunkSize
    ) {
        return skillService.streamSkillsChunked(chunkSize);
    }

    @GetMapping("/{id}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得技能詳情", description = "根據技能 ID 取得技能詳細資訊。")
    public ResponseType<SkillVo> getSkillById(
            @ApiPath(description = "技能唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") UUID id) {
        return ResponseType.Success(skillService.getSkillById(id), "技能查詢成功");
    }

    @PostMapping("/batch-query")
    @RequirePermission("View")
    @ApiOperationOk(summary = "批次依 ID 取得技能清單", description = "傳入技能 ID 清單（最多 100 筆），優先命中細粒度快取，未命中批次查庫並回填快取。")
    public ResponseType<BatchResultVo<SkillVo>> getSkillsByIds(
            @ApiBody(description = "批次技能 ID 請求") BatchIdsRequest request) {
        return ResponseType.Success(skillService.getSkillsByIds(request.getIds()), "批次技能查詢成功");
    }

    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "搜尋技能（分頁）", description = "支援 name、description、createdBy 查詢條件，預設按 createdTime 降序排序")
    public ResponseType<PageResult<SkillVo>> searchSkills(
            @ApiBody(description = "分頁搜尋條件") SkillSearchQuery query) {
        return ResponseType.Success(skillService.searchSkills(query), "技能查詢成功");
    }

    @PutMapping("/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新技能", description = "更新一筆既有的技能。")
    public ResponseType<String> updateSkill(
            @ApiPath(description = "技能唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") UUID id,
            @ApiBody(description = "技能更新資料") SkillVo skill) {
        skill.setId(id);
        skillService.updateSkill(skill);
        return ResponseType.Success("技能更新成功");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除技能", description = "刪除一筆技能。")
    public ResponseType<String> deleteSkill(
            @ApiPath(description = "技能唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") UUID id) {
        SkillVo skill = new SkillVo();
        skill.setId(id);
        skillService.deleteSkill(skill);
        return ResponseType.Success("技能刪除成功");
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
    public ResponseType<PageResult<CurrentUserSkillVo>> searchCurrentUserSkills(
            @ApiBody(description = "分頁搜尋條件") SkillSearchQuery query) {
        return ResponseType.Success(skillService.searchCurrentUserSkills(query), "當前使用者技能查詢成功");
    }
}