package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.DataAccess.IUserProjectSkillDataAccess;
import com.example.BackendArchitectureLab.Entity.UserProjectSkill;
import com.example.BackendArchitectureLab.Repository.UserProjectSkillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserProjectSkillDataAccessImpl implements IUserProjectSkillDataAccess {

    @Autowired
    private UserProjectSkillRepository repository;

    @Override
    public List<UserProjectSkill> findByUserIdAndProjectId(UUID userId, UUID projectId) {
        return repository.findByUserIdAndProjectId(userId, projectId);
    }

    @Override
    public List<UserProjectSkill> findByProjectId(UUID projectId) {
        return repository.findByProjectId(projectId);
    }

    @Override
    public List<UserProjectSkill> findByUserId(UUID userId) {
        return repository.findByUserId(userId);
    }

    @Override
    public Optional<UserProjectSkill> findByUserIdAndProjectIdAndSkillId(
            UUID userId, UUID projectId, UUID skillId) {
        return repository.findByUserIdAndProjectIdAndSkillId(userId, projectId, skillId);
    }

    @Override
    public boolean existsByUserIdAndProjectId(UUID userId, UUID projectId) {
        return repository.existsByUserIdAndProjectId(userId, projectId);
    }

    @Override
    public boolean existsByUserIdAndProjectIdAndSkillId(
            UUID userId, UUID projectId, UUID skillId) {
        return repository.existsByUserIdAndProjectIdAndSkillId(userId, projectId, skillId);
    }

    @Override
    public UserProjectSkill save(UserProjectSkill userProjectSkill) {
        return repository.save(userProjectSkill);
    }

    @Override
    public void deleteByProjectId(UUID projectId) {
        repository.deleteByProjectId(projectId);
    }

    @Override
    public void deleteByUserIdAndProjectId(UUID userId, UUID projectId) {
        repository.deleteByUserIdAndProjectId(userId, projectId);
    }

    @Override
    public void deleteByUserIdAndProjectIdAndSkillId(
            UUID userId, UUID projectId, UUID skillId) {
        repository.deleteByUserIdAndProjectIdAndSkillId(userId, projectId, skillId);
    }

    @Override
    public void deleteBySkillId(UUID skillId) {
        repository.deleteBySkillId(skillId);
    }

    @Override
    public boolean existsBySkillId(UUID skillId) {
        return repository.existsBySkillId(skillId);
    }

    @Override
    public boolean existsBySkillLevelId(UUID skillLevelId) {
        return repository.existsBySkillLevelId(skillLevelId);
    }
}
