package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Repository.UserSkillRepository;
import com.example.BackendArchitectureLab.DataAccess.IUserSkillDataAccess;
import com.example.BackendArchitectureLab.Entity.UserSkill;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class UserSkillDataAccessImpl implements IUserSkillDataAccess {

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Override
    public boolean existsByUserIdAndSkillId(UUID userId, UUID skillId) {
        return userSkillRepository.existsByUserIdAndSkillId(userId, skillId);
    }

    @Override
    public boolean existsBySkillLevelId(UUID skillLevelId) {
        return userSkillRepository.existsBySkillLevelId(skillLevelId);
    }
    
    @Override
    public boolean existsBySkillId(UUID skillId) {
        return userSkillRepository.existsBySkillId(skillId);
    }

    @Override
    public UserSkill save(UserSkill userSkill) {
        return userSkillRepository.save(userSkill);
    }

    @Override
    public void deleteBySkillId(UUID skillId) {
        userSkillRepository.deleteBySkillId(skillId);
    }
    
    @Override
    public void deleteByUserIdAndSkillId(UUID userId, UUID skillId) {
        userSkillRepository.deleteByUserIdAndSkillId(userId, skillId);
    }
    
    @Override
    public List<UserSkill> findByUserId(UUID userId) {
        return userSkillRepository.findByUserId(userId);
    }
    
    @Override
    public List<UserSkill> findBySkillId(UUID skillId) {
        return userSkillRepository.findBySkillId(skillId);
    }
    
    @Override
    public List<UserSkill> findByUserIdAndSkillId(UUID userId, UUID skillId) {
        return userSkillRepository.findByUserIdAndSkillId(userId, skillId);
    }
}
