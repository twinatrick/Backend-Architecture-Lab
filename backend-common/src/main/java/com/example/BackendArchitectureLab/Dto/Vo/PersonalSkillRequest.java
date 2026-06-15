package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 個人技能請求 DTO
 * 用於一般使用者新增或修改個人技能
 */
@Getter
@Setter
@NoArgsConstructor
public class PersonalSkillRequest {
    /**
     * 技能名稱
     */
    private String name;
    
    /**
     * 技能描述
     */
    private String description;
    
    /**
     * 技能等級 ID
     */
    private String skillLevelId;

    /**
     * 手動建立技能等級時使用：等級值
     */
    private Integer skillLevelValue;

    /**
     * 手動建立技能等級時使用：等級標題
     */
    private String skillLevelTitle;

    /**
     * 手動建立技能等級時使用：等級描述
     */
    private String skillLevelDescription;
}
