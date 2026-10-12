package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.UserSkill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSkillRepository extends JpaRepository<UserSkill, UUID> {
    @Override
    @EntityGraph(attributePaths = {"skill", "skillLevel"})
    List<UserSkill> findAll();

    @Override
    @EntityGraph(attributePaths = {"skill", "skillLevel"})
    Page<UserSkill> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"skill", "skillLevel"})
    Optional<UserSkill> findById(UUID id);

    boolean existsByUserIdAndSkillId(UUID userId, UUID skillId);

    boolean existsBySkillLevelId(UUID skillLevelId);
    
    boolean existsBySkillId(UUID skillId);

    @Modifying
    @Query("delete from UserSkill us where us.skill.id = :skillId")
    void deleteBySkillId(@Param("skillId") UUID skillId);
    
    @Modifying
    @Query("delete from UserSkill us where us.userId = :userId and us.skill.id = :skillId")
    void deleteByUserIdAndSkillId(@Param("userId") UUID userId, @Param("skillId") UUID skillId);
    
    @EntityGraph(attributePaths = {"skill", "skillLevel"})
    List<UserSkill> findByUserId(UUID userId);
    
    @EntityGraph(attributePaths = {"skill", "skillLevel"})
    List<UserSkill> findBySkillId(UUID skillId);
    
    @EntityGraph(attributePaths = {"skill", "skillLevel"})
    List<UserSkill> findByUserIdAndSkillId(UUID userId, UUID skillId);
}
