package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ProjectMemberSkillVo {
    private String userId;
    private String userEmail;
    private List<MemberSkillLevelVo> skills;
}
