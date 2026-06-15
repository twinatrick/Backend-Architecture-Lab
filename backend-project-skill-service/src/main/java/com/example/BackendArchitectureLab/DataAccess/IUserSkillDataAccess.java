package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.UserSkill;

import java.util.List;
import java.util.UUID;

public interface IUserSkillDataAccess {
    boolean existsByUserIdAndSkillId(UUID userId, UUID skillId);

    boolean existsBySkillLevelId(UUID skillLevelId);
    
    boolean existsBySkillId(UUID skillId);

    UserSkill save(UserSkill userSkill);

    void deleteBySkillId(UUID skillId);
    
    void deleteByUserIdAndSkillId(UUID userId, UUID skillId);
    
    List<UserSkill> findByUserId(UUID userId);
    
    List<UserSkill> findBySkillId(UUID skillId);
    
    List<UserSkill> findByUserIdAndSkillId(UUID userId, UUID skillId);
}
