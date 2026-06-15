package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.ProjectSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectSkillRepository extends JpaRepository<ProjectSkill, UUID> {
    boolean existsByProjectIdAndSkillId(UUID projectId, UUID skillId);

    boolean existsBySkillLevelId(UUID skillLevelId);
    
    boolean existsBySkillId(UUID skillId);

    Optional<ProjectSkill> findByProjectIdAndSkillId(UUID projectId, UUID skillId);

    List<ProjectSkill> findByProjectId(UUID projectId);

    @Modifying
    @Query("delete from ProjectSkill ps where ps.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") UUID projectId);

    @Modifying
    @Query("delete from ProjectSkill ps where ps.project.id = :projectId and ps.skill.id = :skillId")
    void deleteByProjectIdAndSkillId(@Param("projectId") UUID projectId, @Param("skillId") UUID skillId);

    @Modifying
    @Query("delete from ProjectSkill ps where ps.skill.id = :skillId")
    void deleteBySkillId(@Param("skillId") UUID skillId);
}
