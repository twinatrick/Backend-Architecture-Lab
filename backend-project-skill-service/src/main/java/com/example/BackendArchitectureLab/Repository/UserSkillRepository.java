package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserSkillRepository extends JpaRepository<UserSkill, UUID> {
    boolean existsByUserIdAndSkillId(UUID userId, UUID skillId);

    boolean existsBySkillLevelId(UUID skillLevelId);
    
    boolean existsBySkillId(UUID skillId);

    @Modifying
    @Query("delete from UserSkill us where us.skill.id = :skillId")
    void deleteBySkillId(@Param("skillId") UUID skillId);
    
    @Modifying
    @Query("delete from UserSkill us where us.user.id = :userId and us.skill.id = :skillId")
    void deleteByUserIdAndSkillId(@Param("userId") UUID userId, @Param("skillId") UUID skillId);
    
    List<UserSkill> findByUserId(UUID userId);
    
    List<UserSkill> findBySkillId(UUID skillId);
    
    List<UserSkill> findByUserIdAndSkillId(UUID userId, UUID skillId);
}
