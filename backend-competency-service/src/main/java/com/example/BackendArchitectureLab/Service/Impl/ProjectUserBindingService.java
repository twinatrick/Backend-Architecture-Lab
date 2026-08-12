package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Entity.UserProjectSkill;
import com.example.BackendArchitectureLab.Service.ICompensationOutboxService;
import com.example.BackendArchitectureLab.Service.IProjectUserBindingService;
import com.example.BackendArchitectureLab.Service.IUserGateway;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.MemberSkillLevelVo;
import com.example.BackendArchitectureLab.Vo.ProjectMemberSkillVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ProjectUserBindingService - 專案與使用者/成員技能綁定業務邏輯服務
 */
@Service
public class ProjectUserBindingService implements IProjectUserBindingService {

    @Autowired
    private IProjectDataAccess projectDataAccess;
    @Autowired
    private IUserProjectDataAccess userProjectDataAccess;
    @Autowired
    private IUserProjectSkillDataAccess userProjectSkillDataAccess;
    @Autowired
    private ISkillDataAccess skillDataAccess;
    @Autowired
    private ISkillLevelDataAccess skillLevelDataAccess;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private IUserGateway userGateway;

    @Autowired
    private ICompensationOutboxService compensationOutboxService;

    @Autowired
    @Lazy
    private ProjectUserBindingService self;

    /**
     * 綁定多個使用者到專案
     *
     * @param projectId 專案 ID
     * @param userIds 使用者 ID 列表
     */
    @Override
    public void bindUsersToProject(UUID projectId, List<String> userIds) {
        Project project = projectDataAccess.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        List<UUID> targetUserIds = userIds.stream()
                .map(UUID::fromString)
                .collect(Collectors.toCollection(ArrayList::new));

        // 綁定每個使用者
        for (UUID userId : targetUserIds) {
            // 檢查是否已存在綁定
            if (!userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)) {
                UserProject userProject = new UserProject();
                userProject.setUserId(userId);
                userProject.setProject(project);
                userProjectDataAccess.save(userProject);
            }
        }
    }

    /**
     * 在交易外驗證所有使用者存在（同步 Feign 呼叫不應占用資料庫交易）
     *
     * @param userIds 要驗證的使用者 ID 集合
     * @throws IllegalArgumentException 當任一使用者不存在時拋出
     */
    @Override
    public void validateUsersExist(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (UUID userId : userIds) {
            if (!userGateway.existsUserById(userId)) {
                throw new IllegalArgumentException("User not found: " + userId);
            }
        }
    }

    /**
     * 清除指定使用者的專案列表快取
     *
     * @param userId 使用者 ID
     */
    @Override
    public void evictUserProjectsCache(UUID userId) {
        if (userId == null || cacheManager == null) {
            return;
        }
        Cache cache = cacheManager.getCache("projects");
        if (cache != null) {
            cache.evict("byuser:" + userId);
        }
    }

    @Override
    public void rebindProjectMemberSkills(UUID projectId, Map<UUID, Map<UUID, UUID>> memberSkillsMap) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID must not be null");
        }
        if (memberSkillsMap == null) {
            memberSkillsMap = Map.of();
        }

        // 交易補償（Outbox 模式）：記錄交易意圖與狀態摘要，
        // TRANSACTION_STARTED 與業務交易同 commit，事件由排程批次發送至 Kafka
        UUID transactionId = UUID.randomUUID();
        Map<String, Object> state = buildStateSnapshot(projectId, memberSkillsMap);
        try {
            // 交易外的 Feign 驗證（使用者存在性）
            validateUsersExist(memberSkillsMap.keySet());
            self.doRebindProjectMemberSkills(projectId, memberSkillsMap, transactionId, state);
        } catch (Exception e) {
            // 失敗時寫入 FAILED（@Transactional 已負責 rollback，供消費端追蹤）
            compensationOutboxService.enqueueFailed(transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state, e.getMessage());
            throw e;
        }
    }

    /**
     * 建立補償事件的狀態摘要（僅含數值摘要，不序列化 Entity）
     */
    private Map<String, Object> buildStateSnapshot(UUID projectId, Map<UUID, Map<UUID, UUID>> memberSkillsMap) {
        Map<String, Object> state = new HashMap<>();
        state.put("projectId", projectId.toString());
        state.put("memberCount", memberSkillsMap.size());
        state.put("skillsCount", memberSkillsMap.values().stream().mapToInt(Map::size).sum());
        return state;
    }

    /**
     * 交易內重新綁定成員技能（由 rebindProjectMemberSkills 在交易外的 Feign 驗證後呼叫）
     *
     * @param projectId 專案 ID
     * @param memberSkillsMap 成員技能等級對應
     * @param transactionId 補償交易 ID
     * @param state 補償事件狀態摘要
     */
    @Transactional
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public void doRebindProjectMemberSkills(UUID projectId, Map<UUID, Map<UUID, UUID>> memberSkillsMap,
                                             UUID transactionId, Map<String, Object> state) {
        // TRANSACTION_STARTED 與業務交易同 commit（rollback 時一併消失）
        compensationOutboxService.enqueueTransactionStarted(
                transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state);

        // 驗證專案存在
        Project project = projectDataAccess.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        // 驗證所有使用者已綁定到該專案
        for (UUID userId : memberSkillsMap.keySet()) {
            if (!userProjectDataAccess.existsByUserIdAndProjectId(userId, projectId)) {
                throw new IllegalArgumentException(
                        "User " + userId + " is not a member of project " + projectId
                );
            }
        }

        // 驗證所有技能與等級存在
        for (Map<UUID, UUID> skillLevelMap : memberSkillsMap.values()) {
            for (Map.Entry<UUID, UUID> entry : skillLevelMap.entrySet()) {
                UUID skillId = entry.getKey();
                UUID levelId = entry.getValue();

                Skill skill = skillDataAccess.findById(skillId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));
                SkillLevel skillLevel = skillLevelDataAccess.findById(levelId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill level not found: " + levelId));

                if (!skillLevel.getSkill().getId().equals(skillId)) {
                    throw new IllegalArgumentException("Skill level does not belong to skill");
                }
            }
        }

        // 取得現有的 user_project_skill 綁定
        List<UserProjectSkill> existingBindings = userProjectSkillDataAccess.findByProjectId(projectId);
        Map<UUID, Map<UUID, UserProjectSkill>> existingMap = new HashMap<>();
        for (UserProjectSkill binding : existingBindings) {
            UUID userId = binding.getUserId();
            UUID skillId = binding.getSkill().getId();
            existingMap.computeIfAbsent(userId, k -> new HashMap<>()).put(skillId, binding);
        }

        // 刪除不在目標清單的綁定
        for (Map.Entry<UUID, Map<UUID, UserProjectSkill>> userEntry : existingMap.entrySet()) {
            UUID userId = userEntry.getKey();
            Map<UUID, UserProjectSkill> userSkills = userEntry.getValue();

            if (!memberSkillsMap.containsKey(userId)) {
                // 使用者不在目標清單，刪除該使用者在此專案的所有技能
                for (UUID skillId : userSkills.keySet()) {
                    userProjectSkillDataAccess.deleteByUserIdAndProjectIdAndSkillId(userId, projectId, skillId);
                }
            } else {
                // 使用者在目標清單，刪除不在目標技能清單的技能
                Map<UUID, UUID> targetSkills = memberSkillsMap.get(userId);
                for (UUID skillId : userSkills.keySet()) {
                    if (!targetSkills.containsKey(skillId)) {
                        userProjectSkillDataAccess.deleteByUserIdAndProjectIdAndSkillId(userId, projectId, skillId);
                    }
                }
            }
        }

        // 新增或更新目標綁定
        for (Map.Entry<UUID, Map<UUID, UUID>> memberEntry : memberSkillsMap.entrySet()) {
            UUID userId = memberEntry.getKey();
            Map<UUID, UUID> targetSkills = memberEntry.getValue();

            for (Map.Entry<UUID, UUID> skillEntry : targetSkills.entrySet()) {
                UUID skillId = skillEntry.getKey();
                UUID levelId = skillEntry.getValue();

                UserProjectSkill existingBinding = existingMap
                        .getOrDefault(userId, Map.of())
                        .get(skillId);

                if (existingBinding == null) {
                    // 新增
                    UserProjectSkill newBinding = new UserProjectSkill();
                    newBinding.setUserId(userId);
                    newBinding.setProject(project);
                    newBinding.setSkill(skillDataAccess.findById(skillId).orElseThrow());
                    newBinding.setSkillLevel(skillLevelDataAccess.findById(levelId).orElseThrow());
                    userProjectSkillDataAccess.save(newBinding);
                } else {
                    // 更新等級
                    UUID existingLevelId = existingBinding.getSkillLevel().getId();
                    if (!existingLevelId.equals(levelId)) {
                        SkillLevel newLevel = skillLevelDataAccess.findById(levelId).orElseThrow();
                        existingBinding.setSkillLevel(newLevel);
                        userProjectSkillDataAccess.save(existingBinding);
                    }
                }
            }
        }

        // COMMITTED 與業務資料同交易 commit（rollback 時一併消失）；失敗則由 rebind 層以 REQUIRES_NEW 寫入 FAILED
        compensationOutboxService.enqueueCommitted(
                transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state);
    }

    @Override
    public List<ProjectMemberSkillVo> getProjectMemberSkills(UUID projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID must not be null");
        }

        if (!projectDataAccess.existsById(projectId)) {
            throw new IllegalArgumentException("Project not found");
        }

        Map<UUID, List<UserProjectSkill>> bindingsByUser = userProjectSkillDataAccess.findByProjectId(projectId)
                .stream()
                .collect(Collectors.groupingBy(UserProjectSkill::getUserId));

        return userProjectDataAccess.findByProjectId(projectId).stream()
                .map(userProject -> {
                    UUID userId = userProject.getUserId();
                    ProjectMemberSkillVo vo = new ProjectMemberSkillVo();
                    vo.setUserId(userId.toString());
                    vo.setUserEmail("");

                    List<MemberSkillLevelVo> skills = bindingsByUser
                            .getOrDefault(userId, List.of())
                            .stream()
                            .map(this::toMemberSkillLevelVo)
                            .collect(Collectors.toCollection(ArrayList::new));
                    vo.setSkills(skills);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private MemberSkillLevelVo toMemberSkillLevelVo(UserProjectSkill binding) {
        MemberSkillLevelVo vo = new MemberSkillLevelVo();
        vo.setSkillId(binding.getSkill().getId().toString());
        vo.setSkillName(binding.getSkill().getName());

        SkillLevel level = binding.getSkillLevel();
        if (level != null) {
            vo.setSkillLevelId(level.getId().toString());
            vo.setLevelTitle(level.getTitle());
            vo.setLevelValue(level.getLevelValue());
        }
        return vo;
    }
}
