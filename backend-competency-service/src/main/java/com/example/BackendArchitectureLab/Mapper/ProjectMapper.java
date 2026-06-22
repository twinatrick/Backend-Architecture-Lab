package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Vo.ProjectVo;
import com.example.BackendArchitectureLab.Entity.Project;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProjectMapper {
    ProjectVo toVo(Project project);

    Project toEntity(ProjectVo projectVo);
}
