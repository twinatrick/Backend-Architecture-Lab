package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.UUID;
@Getter
@Setter
@NoArgsConstructor
public class SkillVo {
    private UUID id;
    //skill Name
    private String name;

    //技能描述
    private String description;
    private String createdBy;
    private String updatedBy;
    private Date createdTime;
    private Date updatedTime;
    
    /**
     * 管理者模式使用：要綁定的使用者 ID 陣列
     */
    private List<String> userIds;
    
    /**
     * 管理者模式使用：預設技能等級 ID
     */
    private String skillLevelId;
}
