package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.ProjectSkill;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Service.IProjectSkillService;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.ProjectSkillVo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ProjectSkillService - 專案技能綁定業務邏輯服務
 */
@Service
@RequiredArgsConstructor
public class ProjectSkillService implements IProjectSkillService {

    private final TransactionExecutor transactionExecutor;
    private final IProjectDataAccess projectDataAccess;
    private final IProjectSkillDataAccess projectSkillDataAccess;
    private final IUserProjectDataAccess userProjectDataAccess;
    private final IUserSkillDataAccess userSkillDataAccess;
    private final ISkillLevelDataAccess skillLevelDataAccess;
    private final ISkillDataAccess skillDataAccess;
    private final SecurityUtil securityUtil;
    @org.springframework.context.annotation.Lazy
    private final ProjectSkillService self;

    @Override
    public List<ProjectSkillVo> getProjectSkills(UUID projectId) {
        return getProjectSkillsCache(projectId).getData();
    }

    @Override
    @Cacheable(value = "projectSkills", key = "#projectId", sync = true)
    public CacheListWrapper<ProjectSkillVo> getProjectSkillsCache(UUID projectId) {
        return transactionExecutor.executeReadOnly(() -> {
            if (projectId == null) {
                throw new IllegalArgumentException("Project ID must not be null");
            }

            if (!projectDataAccess.existsById(projectId)) {
                throw new IllegalArgumentException("Project not found");
            }

            List<ProjectSkill> projectSkills = projectSkillDataAccess.findByProjectId(projectId);

            List<ProjectSkillVo> list = projectSkills.stream().map(ps -> {
                ProjectSkillVo vo = new ProjectSkillVo();
                vo.setProjectId(ps.getProject().getId());
                vo.setSkillId(ps.getSkill().getId());
                vo.setSkillName(ps.getSkill().getName());
                vo.setSkillDescription(ps.getSkill().getDescription());

                SkillLevel level = ps.getSkillLevel();
                if (level != null) {
                    vo.setSkillLevelId(level.getId());
                    vo.setLevelValue(level.getLevelValue());
                    vo.setLevelTitle(level.getTitle());
                    vo.setLevelDescription(level.getDescription());
                }
                return vo;
            }).toList();
            return new CacheListWrapper<>(list);
        });
    }

    @Override
    public List<ProjectSkillVo> getPersonalProjectSkills(UUID projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID must not be null");
        }

        UUID currentUserId = securityUtil.requireCurrentUserId();

        // 驗證是否為可見專案
        if (!userProjectDataAccess.existsByUserIdAndProjectId(currentUserId, projectId)) {
            throw new IllegalArgumentException("You do not have access to this project");
        }

        return getProjectSkills(projectId);
    }

    @Transactional
    @Override
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public void bindPersonalProjectSkill(UUID projectId, UUID skillId, UUID skillLevelId) {
        UUID currentUserId = securityUtil.requireCurrentUserId();
        validateBindingInput(projectId, skillId, skillLevelId);
        ensureCanManageProjectBinding(projectId, currentUserId);
        ensureSkillVisibleToCurrentUser(skillId, currentUserId);

        if (projectSkillDataAccess.existsByProjectIdAndSkillId(projectId, skillId)) {
            throw new IllegalArgumentException("Skill already bind to project");
        }

        Project project = projectDataAccess.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        SkillLevel skillLevel = resolveAndValidateSkillLevel(skillId, skillLevelId);

        ProjectSkill projectSkill = new ProjectSkill();
        projectSkill.setProject(project);
        projectSkill.setSkill(skillDataAccess.findById(skillId).orElseThrow());
        projectSkill.setSkillLevel(skillLevel);
        projectSkillDataAccess.save(projectSkill);
    }

    @Transactional
    @Override
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public void updatePersonalProjectSkillLevel(UUID projectId, UUID skillId, UUID skillLevelId) {
        UUID currentUserId = securityUtil.requireCurrentUserId();
        validateBindingInput(projectId, skillId, skillLevelId);
        ensureCanManageProjectBinding(projectId, currentUserId);
        ensureSkillVisibleToCurrentUser(skillId, currentUserId);

        ProjectSkill projectSkill = projectSkillDataAccess.findByProjectIdAndSkillId(projectId, skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill binding not found for project"));
        SkillLevel skillLevel = resolveAndValidateSkillLevel(skillId, skillLevelId);

        projectSkill.setSkillLevel(skillLevel);
        projectSkillDataAccess.save(projectSkill);
    }

    @Transactional
    @Override
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public void unbindPersonalProjectSkill(UUID projectId, UUID skillId) {
        UUID currentUserId = securityUtil.requireCurrentUserId();
        if (projectId == null || skillId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        ensureCanManageProjectBinding(projectId, currentUserId);

        if (!projectSkillDataAccess.existsByProjectIdAndSkillId(projectId, skillId)) {
            throw new IllegalArgumentException("Skill binding not found for project");
        }

        projectSkillDataAccess.deleteByProjectIdAndSkillId(projectId, skillId);
    }

    @Transactional
    @Override
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public void rebindProjectSkills(UUID projectId, Map<UUID, UUID> skillLevelMapping) {
        if (projectId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        Project project = projectDataAccess.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        Map<UUID, UUID> targetMap = normalizeSkillLevelMapping(skillLevelMapping);
        validateProjectSkillLevelMapping(targetMap);

        Map<UUID, ProjectSkill> existingMap = projectSkillDataAccess.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(ps -> ps.getSkill().getId(), ps -> ps));

        for (UUID existingSkillId : existingMap.keySet()) {
            if (!targetMap.containsKey(existingSkillId)) {
                projectSkillDataAccess.deleteByProjectIdAndSkillId(projectId, existingSkillId);
            }
        }

        for (Map.Entry<UUID, UUID> entry : targetMap.entrySet()) {
            UUID skillId = entry.getKey();
            UUID levelId = entry.getValue();
            ProjectSkill existing = existingMap.get(skillId);

            if (existing == null) {
                ProjectSkill projectSkill = new ProjectSkill();
                projectSkill.setProject(project);
                projectSkill.setSkill(skillDataAccess.findById(skillId).orElseThrow());
                projectSkill.setSkillLevel(skillLevelDataAccess.findById(levelId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill level not found")));
                projectSkillDataAccess.save(projectSkill);
                continue;
            }

            UUID currentLevelId = existing.getSkillLevel() == null ? null : existing.getSkillLevel().getId();
            if (!Objects.equals(currentLevelId, levelId)) {
                existing.setSkillLevel(skillLevelDataAccess.findById(levelId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill level not found")));
                projectSkillDataAccess.save(existing);
            }
        }
    }

    @Transactional
    @Override
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public void rebindPersonalProjectSkills(UUID projectId, Map<UUID, UUID> skillLevelMapping) {
        UUID currentUserId = securityUtil.requireCurrentUserId();
        if (projectId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        ensureCanManageProjectBinding(projectId, currentUserId);

        Map<UUID, UUID> targetMap = normalizeSkillLevelMapping(skillLevelMapping);
        for (UUID skillId : targetMap.keySet()) {
            ensureSkillVisibleToCurrentUser(skillId, currentUserId);
        }

        self.rebindProjectSkills(projectId, targetMap);
    }

    @Override
    public boolean existsProjectSkillByLevelId(UUID levelId) {
        return projectSkillDataAccess.existsBySkillLevelId(levelId);
    }

    @Override
    public void deleteProjectSkillsBySkillId(UUID skillId) {
        projectSkillDataAccess.deleteBySkillId(skillId);
    }

    private void validateBindingInput(UUID projectId, UUID skillId, UUID skillLevelId) {
        if (projectId == null || skillId == null || skillLevelId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
    }

    private void ensureCanManageProjectBinding(UUID projectId, UUID currentUserId) {
        if (!userProjectDataAccess.existsByUserIdAndProjectId(currentUserId, projectId)) {
            throw new IllegalArgumentException("You are not allowed to manage bindings for this project");
        }
    }

    private void ensureSkillVisibleToCurrentUser(UUID skillId, UUID currentUserId) {
        Set<UUID> visibleSkillIds = userSkillDataAccess.findByUserId(currentUserId).stream()
                .map(userSkill -> userSkill.getSkill().getId())
                .collect(Collectors.toSet());

        List<UserProject> userProjects = userProjectDataAccess.findByUserId(currentUserId);
        if (!userProjects.isEmpty()) {
            List<UUID> projectIds = userProjects.stream()
                .map(up -> up.getProject().getId())
                .toList();
            projectSkillDataAccess.findByProjectIdIn(projectIds).stream()
                .map(ps -> ps.getSkill().getId())
                .forEach(visibleSkillIds::add);
        }

        if (!visibleSkillIds.contains(skillId)) {
            throw new IllegalArgumentException("Skill is not visible to current user");
        }
    }

    private SkillLevel resolveAndValidateSkillLevel(UUID skillId, UUID skillLevelId) {
        SkillLevel skillLevel = skillLevelDataAccess.findById(skillLevelId)
                .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));
        if (!skillLevel.getSkill().getId().equals(skillId)) {
            throw new IllegalArgumentException("Skill level does not belong to skill");
        }
        return skillLevel;
    }

    private Map<UUID, UUID> normalizeSkillLevelMapping(Map<UUID, UUID> skillLevelMapping) {
        if (skillLevelMapping == null || skillLevelMapping.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UUID> normalized = new LinkedHashMap<>();
        for (Map.Entry<UUID, UUID> entry : skillLevelMapping.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            normalized.put(entry.getKey(), entry.getValue());
        }
        return normalized;
    }

    private void validateProjectSkillLevelMapping(Map<UUID, UUID> mapping) {
        for (Map.Entry<UUID, UUID> entry : mapping.entrySet()) {
            UUID skillId = entry.getKey();
            UUID levelId = entry.getValue();
            SkillLevel level = skillLevelDataAccess.findById(levelId)
                    .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));
            if (!level.getSkill().getId().equals(skillId)) {
                throw new IllegalArgumentException("Skill level does not belong to skill");
            }
        }
    }
}
