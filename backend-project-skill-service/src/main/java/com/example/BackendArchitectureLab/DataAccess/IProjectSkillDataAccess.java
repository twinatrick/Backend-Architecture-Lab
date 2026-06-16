package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.ProjectSkill;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IProjectSkillDataAccess {
    boolean existsByProjectIdAndSkillId(UUID projectId, UUID skillId);

    boolean existsBySkillLevelId(UUID skillLevelId);
    
    boolean existsBySkillId(UUID skillId);

    ProjectSkill save(ProjectSkill projectSkill);

    Optional<ProjectSkill> findByProjectIdAndSkillId(UUID projectId, UUID skillId);

    List<ProjectSkill> findByProjectId(UUID projectId);

    List<ProjectSkill> findByProjectIdIn(List<UUID> projectIds);

    void deleteByProjectId(UUID projectId);

    void deleteByProjectIdAndSkillId(UUID projectId, UUID skillId);

    void deleteBySkillId(UUID skillId);
}
