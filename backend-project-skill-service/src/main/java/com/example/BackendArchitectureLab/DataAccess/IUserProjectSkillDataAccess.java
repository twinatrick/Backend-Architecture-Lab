package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.UserProjectSkill;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IUserProjectSkillDataAccess {

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

    UserProjectSkill save(UserProjectSkill userProjectSkill);

    void deleteByProjectId(UUID projectId);

    void deleteByUserIdAndProjectId(UUID userId, UUID projectId);

    void deleteByUserIdAndProjectIdAndSkillId(
            UUID userId, UUID projectId, UUID skillId
    );

    void deleteBySkillId(UUID skillId);

    boolean existsBySkillId(UUID skillId);

    boolean existsBySkillLevelId(UUID skillLevelId);
}
