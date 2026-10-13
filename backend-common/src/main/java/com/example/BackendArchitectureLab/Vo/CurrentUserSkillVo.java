package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * 當前使用者技能 VO
 * 包含技能來源資訊（USER 直接綁定或 PROJECT 專案技能）
 */
@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "當前使用者技能資訊（含來源）")
public class CurrentUserSkillVo {
    @ApiField(description = "技能 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private UUID id;

    @ApiField(description = "技能名稱", example = "Java")
    private String name;

    @ApiField(description = "技能描述", example = "後端主力語言")
    private String description;

    @ApiField(description = "建立者 UUID", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;

    @ApiField(description = "更新者 UUID", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String updatedBy;

    @ApiField(description = "建立時間戳記")
    private Date createdTime;

    @ApiField(description = "更新時間戳記")
    private Date updatedTime;

    @ApiField(description = "來源類型 (USER: 直接綁定, PROJECT: 專案技能)", example = "USER")
    private String sourceType;

    @ApiField(description = "專案 UUID（當 sourceType 為 PROJECT 時有值）", example = "6ba7b810-9dad-11d1-80b4-00c04fd430c8", format = "uuid")
    private UUID projectId;

    @ApiField(description = "專案名稱（當 sourceType 為 PROJECT 時有值）", example = "微服務重構專案")
    private String projectName;

    /**
     * 從 SkillVo 建立 CurrentUserSkillVo（USER 來源）
     */
    public static CurrentUserSkillVo fromSkillVo(SkillVo skillVo) {
        CurrentUserSkillVo vo = new CurrentUserSkillVo();
        vo.setId(skillVo.getId());
        vo.setName(skillVo.getName());
        vo.setDescription(skillVo.getDescription());
        vo.setCreatedBy(skillVo.getCreatedBy());
        vo.setUpdatedBy(skillVo.getUpdatedBy());
        vo.setCreatedTime(skillVo.getCreatedTime());
        vo.setUpdatedTime(skillVo.getUpdatedTime());
        vo.setSourceType("USER");
        return vo;
    }

    /**
     * 從 SkillVo 建立 CurrentUserSkillVo（PROJECT 來源）
     */
    public static CurrentUserSkillVo fromSkillVoWithProject(SkillVo skillVo, UUID projectId, String projectName) {
        CurrentUserSkillVo vo = new CurrentUserSkillVo();
        vo.setId(skillVo.getId());
        vo.setName(skillVo.getName());
        vo.setDescription(skillVo.getDescription());
        vo.setCreatedBy(skillVo.getCreatedBy());
        vo.setUpdatedBy(skillVo.getUpdatedBy());
        vo.setCreatedTime(skillVo.getCreatedTime());
        vo.setUpdatedTime(skillVo.getUpdatedTime());
        vo.setSourceType("PROJECT");
        vo.setProjectId(projectId);
        vo.setProjectName(projectName);
        return vo;
    }
}
