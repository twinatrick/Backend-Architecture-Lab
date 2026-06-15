package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProjectSkillBindRequest {
    private String projectId;
    private String skillId;
    private String skillLevelId;
}
