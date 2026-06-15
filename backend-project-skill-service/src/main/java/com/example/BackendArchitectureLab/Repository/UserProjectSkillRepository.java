package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.UserProjectSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProjectSkillRepository extends JpaRepository<UserProjectSkill, UUID> {

    List<UserProjectSkill> findByUserIdAndProjectId(UUID userId, UUID projectId);

    List<UserProjectSkill> findByProjectId(UUID projectId);

    List<UserProjectSkill> findByUserId(UUID userId);

    Optional<UserProjectSkill> findByUserIdAndProjectIdAndSkillId(
            UUID userId, UUID projectId, UUID skillId
    );

    boolean existsByUserIdAndProjectId(UUID userId, UUID projectId);

    boolean existsByUserIdAndProjectIdAndSkillId(
            UUID userId, UUID projectId, UUID skillId
    );

    @Modifying
    @Query("delete from UserProjectSkill ups where ups.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") UUID projectId);

    @Modifying
    @Query("delete from UserProjectSkill ups where ups.user.id = :userId and ups.project.id = :projectId")
    void deleteByUserIdAndProjectId(
            @Param("userId") UUID userId,
            @Param("projectId") UUID projectId
    );

    @Modifying
    @Query("delete from UserProjectSkill ups where ups.user.id = :userId and ups.project.id = :projectId and ups.skill.id = :skillId")
    void deleteByUserIdAndProjectIdAndSkillId(
            @Param("userId") UUID userId,
            @Param("projectId") UUID projectId,
            @Param("skillId") UUID skillId
    );

    @Modifying
    @Query("delete from UserProjectSkill ups where ups.skill.id = :skillId")
    void deleteBySkillId(@Param("skillId") UUID skillId);

    boolean existsBySkillId(UUID skillId);

    boolean existsBySkillLevelId(UUID skillLevelId);
}
