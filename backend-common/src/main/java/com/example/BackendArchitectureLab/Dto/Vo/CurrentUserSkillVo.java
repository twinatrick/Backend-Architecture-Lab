package com.example.BackendArchitectureLab.Dto.Vo;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "當前使用者技能資訊（含來源）")
public class CurrentUserSkillVo {
    @Schema(description = "技能ID")
    private UUID id;
    
    @Schema(description = "技能名稱")
    private String name;
    
    @Schema(description = "技能描述")
    private String description;
    
    @Schema(description = "創建者")
    private String createdBy;
    
    @Schema(description = "更新者")
    private String updatedBy;
    
    @Schema(description = "創建時間")
    private Date createdTime;
    
    @Schema(description = "更新時間")
    private Date updatedTime;
    
    @Schema(description = "來源類型：USER（直接綁定）或 PROJECT（專案技能）。管理者指派到使用者的技能屬於 USER 來源但視為唯讀（不可透過個人技能 API 修改內容）；可依權限進行綁定關聯。")
    private String sourceType;
    
    @Schema(description = "專案ID（當 sourceType 為 PROJECT 時有值）")
    private UUID projectId;
    
    @Schema(description = "專案名稱（當 sourceType 為 PROJECT 時有值）")
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
