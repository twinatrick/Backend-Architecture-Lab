package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Service.ISkillService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiBody;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiPath;
import com.example.BackendArchitectureLab.Dto.Request.PersonalSkillLevelRequest;
import com.example.BackendArchitectureLab.Dto.Request.PersonalSkillRequest;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.SkillVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/skill/personal")
@RequiredArgsConstructor
@ApiControllerTag(name = "Personal Skills", description = "個人技能管理相關 API")
public class PersonalSkillController {
    private final ISkillService skillService;

    @PostMapping
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增個人技能", description = "一般使用者新增技能，會自動綁定到當前登入使用者。若未提供 skillLevelId，可手動填寫等級值、標題與描述建立第一個技能等級。")
    public ResponseType<SkillVo> addPersonalSkill(
            @ApiBody(description = "新增個人技能請求資料") PersonalSkillRequest request) {
        return ResponseType.Success(skillService.addPersonalSkill(request), "個人技能新增成功");
    }

    @PutMapping("/{skillId}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "修改個人技能", description = "只有技能的擁有者可以修改技能主資料。管理者指派給使用者的技能視為唯讀，不可透過個人修改 API 變更 name/description 等內容。")
    public ResponseType<String> updatePersonalSkill(
            @ApiPath(name = "skillId", description = "目標技能 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid") UUID skillId,
            @ApiBody(description = "修改個人技能請求資料") PersonalSkillRequest request) {
        skillService.updatePersonalSkill(skillId, request);
        return ResponseType.Success("個人技能修改成功");
    }

    @PutMapping("/{skillId}/level")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "修改個人技能綁定等級", description = "更新 user-skill 關聯上的 skill level，不會修改技能主資料。")
    public ResponseType<String> updatePersonalSkillLevel(
            @ApiPath(name = "skillId", description = "目標技能 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid") UUID skillId,
            @ApiBody(description = "修改個人技能等級請求資料") PersonalSkillLevelRequest request) {
        skillService.updatePersonalSkillLevel(skillId, UUID.fromString(request.getSkillLevelId()));
        return ResponseType.Success("個人技能綁定等級修改成功");
    }

    @DeleteMapping("/{skillId}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "解除個人技能綁定", description = "移除 user-skill 綁定。管理者指派技能雖不可修改主資料，仍可解除個人綁定。")
    public ResponseType<String> deletePersonalSkill(
            @ApiPath(name = "skillId", description = "目標技能 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid") UUID skillId) {
        skillService.deletePersonalSkill(skillId);
        return ResponseType.Success("個人技能綁定解除成功");
    }
}