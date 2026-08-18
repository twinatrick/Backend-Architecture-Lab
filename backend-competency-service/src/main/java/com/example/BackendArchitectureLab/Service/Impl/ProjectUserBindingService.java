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
import com.example.BackendArchitectureLab.Service.IExternalSyncCommandService;
import com.example.BackendArchitectureLab.Service.IProjectUserBindingService;
import com.example.BackendArchitectureLab.Service.IUserGateway;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.MemberSkillLevelVo;
import com.example.BackendArchitectureLab.Vo.ProjectMemberSkillVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ProjectUserBindingService - 專案與使用者/成員技能綁定業務邏輯服務
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectUserBindingService implements IProjectUserBindingService {

    private final IProjectDataAccess projectDataAccess;
    private final IUserProjectDataAccess userProjectDataAccess;
    private final IUserProjectSkillDataAccess userProjectSkillDataAccess;
    private final ISkillDataAccess skillDataAccess;
    private final ISkillLevelDataAccess skillLevelDataAccess;
    private final CacheManager cacheManager;
    private final IUserGateway userGateway;
    private final ICompensationOutboxService compensationOutboxService;
    private final IExternalSyncCommandService externalSyncCommandService;

    @Lazy
    private final ProjectUserBindingService self;

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

        // 1. 交易外的 Feign 驗證：若失敗直接拋出，不需寫入 any 補償 Outbox
        validateUsersExist(memberSkillsMap.keySet());

        UUID transactionId = UUID.randomUUID();

        // 2. 執行本地資料庫事務（內部包含外部同步 durable command 的同 commit 寫入）
        //    本地事務 commit 之後，外部同步由 ExternalSyncWorker 依 durable command 可靠執行：
        //    即使 JVM 在 commit 與實際同步之間 crash，命令仍存在於 external_sync_command，
        //    worker 遲早會執行，不再有「commit 後同步永不執行」的 crash window。
        //    同步失敗重試耗盡（DEAD）時才觸發補償閉環（COMPENSATION_REQUIRED）。
        //    本地事務執行失敗已由 Spring 負責 rollback，此處不需發送補償事件，直接向上拋出。
        self.doRebindProjectMemberSkills(projectId, memberSkillsMap, transactionId);
    }

    /**
     * 建立補償事件的狀態摘要（含數值摘要與還原所需的歷史 bindings 明細）
     */
    private Map<String, Object> buildStateSnapshot(UUID projectId, Map<UUID, Map<UUID, UUID>> memberSkillsMap) {
        Map<String, Object> state = new HashMap<>();
        state.put("projectId", projectId.toString());
        state.put("memberCount", memberSkillsMap.size());
        state.put("skillsCount", memberSkillsMap.values().stream().mapToInt(Map::size).sum());

        // 額外讀取目前的 user_project_skill 綁定，作為歷史 before-state 保存！
        List<UserProjectSkill> existingBindings = userProjectSkillDataAccess.findByProjectId(projectId);
        List<Map<String, String>> bindingsList = existingBindings.stream().map(b -> {
            Map<String, String> m = new HashMap<>();
            m.put("userId", b.getUserId().toString());
            m.put("skillId", b.getSkill().getId().toString());
            m.put("levelId", b.getSkillLevel().getId().toString());
            return m;
        }).collect(Collectors.toList());
        state.put("bindings", bindingsList);

        return state;
    }

    /**
     * 交易內重新綁定成員技能（由 rebindProjectMemberSkills 在交易外的 Feign 驗證後呼叫）
     *
     * @param projectId 專案 ID
     * @param memberSkillsMap 成員技能等級對應
     * @param transactionId 補償交易 ID
     * @return 建立的一致性快照 state
     */
    @Transactional
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public Map<String, Object> doRebindProjectMemberSkills(UUID projectId, Map<UUID, Map<UUID, UUID>> memberSkillsMap,
                                                            UUID transactionId) {
        // 驗證專案存在
        Project project = projectDataAccess.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        // Touch 專案的 updatedTime 並強制 Flush（觸發 Auditing 與樂觀鎖 version 遞增），以進行補償時的樂觀防護。
        // 不變式：任何對 user_project_skill 的寫入（rebind / restore / 未來新增的直接綁定端點）都必須在同一個
        // 交易內 bump Project.version，否則補償還原的 expectedVersion / commit-time CAS 守衛將失去保障。
        project.setUpdatedTime(new Date());
        project = projectDataAccess.saveAndFlush(project);

        // 交易內建立 consistent snapshot
        Map<String, Object> state = buildStateSnapshot(projectId, memberSkillsMap);
        state.put("expectedVersion", project.getVersion() != null ? project.getVersion() : 0L);

        // TRANSACTION_STARTED 與業務交易同 commit（rollback 時一併消失）
        compensationOutboxService.enqueueTransactionStarted(
                transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state);

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

        // 外部同步 durable command 與業務資料同 commit：啟用時寫入命令，由 ExternalSyncWorker 可靠執行，
        // 消除「本地 commit 後 JVM crash 導致外部同步永不執行」的 crash window。
        if (externalSyncCommandService.isEnabled()) {
            externalSyncCommandService.enqueue(transactionId, projectId, memberSkillsMap, state);
        }

        return state;
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
