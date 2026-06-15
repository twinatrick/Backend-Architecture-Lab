package com.example.BackendArchitectureLab.Dto.Vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "專案綁定的技能與詳細等級資訊")
public class ProjectSkillVo {
    
    @Schema(description = "專案ID")
    private UUID projectId;

    @Schema(description = "技能ID")
    private UUID skillId;

    @Schema(description = "技能名稱")
    private String skillName;

    @Schema(description = "技能描述")
    private String skillDescription;

    @Schema(description = "技能等級ID")
    private UUID skillLevelId;

    @Schema(description = "技能等級數值")
    private Integer levelValue;

    @Schema(description = "技能等級標題")
    private String levelTitle;

    @Schema(description = "技能等級描述")
    private String levelDescription;
}
