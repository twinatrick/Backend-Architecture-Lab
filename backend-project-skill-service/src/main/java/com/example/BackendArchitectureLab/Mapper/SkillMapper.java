package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Dto.Vo.SkillVo;
import com.example.BackendArchitectureLab.Entity.Skill;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SkillMapper {
    SkillVo toVo(Skill skill);

    Skill toEntity(SkillVo skillVo);
}
