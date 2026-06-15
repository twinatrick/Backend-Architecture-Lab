package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Repository.ProjectSkillRepository;
import com.example.BackendArchitectureLab.DataAccess.IProjectSkillDataAccess;
import com.example.BackendArchitectureLab.Entity.ProjectSkill;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProjectSkillDataAccessImpl implements IProjectSkillDataAccess {

    @Autowired
    private ProjectSkillRepository projectSkillRepository;

    @Override
    public boolean existsByProjectIdAndSkillId(UUID projectId, UUID skillId) {
        return projectSkillRepository.existsByProjectIdAndSkillId(projectId, skillId);
    }

    @Override
    public boolean existsBySkillLevelId(UUID skillLevelId) {
        return projectSkillRepository.existsBySkillLevelId(skillLevelId);
    }
    
    @Override
    public boolean existsBySkillId(UUID skillId) {
        return projectSkillRepository.existsBySkillId(skillId);
    }

    @Override
    public ProjectSkill save(ProjectSkill projectSkill) {
        return projectSkillRepository.save(projectSkill);
    }

    @Override
    public Optional<ProjectSkill> findByProjectIdAndSkillId(UUID projectId, UUID skillId) {
        return projectSkillRepository.findByProjectIdAndSkillId(projectId, skillId);
    }

    @Override
    public List<ProjectSkill> findByProjectId(UUID projectId) {
        return projectSkillRepository.findByProjectId(projectId);
    }

    @Override
    public void deleteByProjectId(UUID projectId) {
        projectSkillRepository.deleteByProjectId(projectId);
    }

    @Override
    public void deleteByProjectIdAndSkillId(UUID projectId, UUID skillId) {
        projectSkillRepository.deleteByProjectIdAndSkillId(projectId, skillId);
    }

    @Override
    public void deleteBySkillId(UUID skillId) {
        projectSkillRepository.deleteBySkillId(skillId);
    }
}
