package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberSkillLevelVo {
    private String skillId;
    private String skillName;
    private String skillLevelId;
    private String levelTitle;
    private Integer levelValue;
}
