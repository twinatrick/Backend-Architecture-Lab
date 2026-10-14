package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "專案成員技能資訊物件")
public class ProjectMemberSkillVo {
    @ApiField(description = "成員使用者 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private String userId;

    @ApiField(description = "成員電子信箱", example = "member@example.com")
    private String userEmail;

    @ApiField(description = "成員具備之技能清單")
    private List<MemberSkillLevelVo> skills;
}
