package com.example.BackendArchitectureLab.Vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PersonalProjectSkillBindRequest {
    private String skillId;
    private String skillLevelId;
}
