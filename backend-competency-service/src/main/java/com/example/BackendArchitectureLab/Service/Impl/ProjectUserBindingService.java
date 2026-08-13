package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.Entity.CompensationRestoreLog;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Entity.UserProjectSkill;
import com.example.BackendArchitectureLab.Exception.CompensationConflictException;
import com.example.BackendArchitectureLab.Repository.CompensationRestoreLogRepository;
import com.example.BackendArchitectureLab.Service.ICompensationOutboxService;
import com.example.BackendArchitectureLab.Service.IProjectUserBindingService;
import com.example.BackendArchitectureLab.Service.IUserGateway;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.MemberSkillLevelVo;
import com.example.BackendArchitectureLab.Vo.ProjectMemberSkillVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
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
    private CompensationRestoreLogRepository restoreLogRepository;

    @Value("${compensation.restore.lease-seconds:300}")
    private long restoreLeaseSeconds;

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

        // 1. 交易外的 Feign 驗證：若失敗直接拋出，不需寫入 any 補償 Outbox
        validateUsersExist(memberSkillsMap.keySet());

        UUID transactionId = UUID.randomUUID();
        Map<String, Object> state;

        // 2. 執行本地資料庫事務
        try {
            state = self.doRebindProjectMemberSkills(projectId, memberSkillsMap, transactionId);
        } catch (Exception e) {
            // 本地事務執行失敗已由 Spring 負責 rollback，此處不需發送補償事件，直接拋出
            throw e;
        }

        // 3. 本地事務成功 Commit 後，進行外部系統同步
        try {
            // 模擬外部系統同步（無法自動 rollback 事務）
            // 在測試中若傳入包含 00000000-0000-0000-0000-000000000000 UUID，則拋出異常模擬同步失敗，觸發補償機制！
            boolean triggerFailure = memberSkillsMap.containsKey(UUID.fromString("00000000-0000-0000-0000-000000000000"));
            if (triggerFailure) {
                throw new RuntimeException("Simulated external partner sync failed after DB commit");
            }
        } catch (Exception e) {
            // 僅在「本地已 commit 且外部同步失敗」時，才需要非同步補償
            // 失敗時：同一 REQUIRES_NEW 交易內寫入 FAILED（失敗事實）與 COMPENSATION_REQUIRED（補償請求閉環），確保兩者同 commit
            compensationOutboxService.enqueueFailureAndCompensationRequired(
                    transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state, e.getMessage());
            throw e;
        }
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

    /**
     * 原子認領補償還原事件（資料庫級 Idempotency Guard + Fencing Token）。
     * 以 eventId 主鍵在獨立交易（REQUIRES_NEW）中進行 atomic claim：
     * - 已存在且狀態為 SUCCESS → 拒絕認領（先前的消費者已完成處理）
     * - 全新 eventId → 以 PROCESSING 插入並記錄新的 ownerId/fencingVersion/租約
     * - 已存在 FAILED → 以新 token 直接接管
     * - 已存在 PROCESSING 且租約未到期 → 拒絕（他人仍在使用）
     * - 已存在 PROCESSING 且租約到期 → 僅當新 fencingVersion 更大時由 takeOverClaim CAS 接管
     * - 高並發下 concurrent insert 觸發主鍵衝突時 → 回傳 false 拒絕認領
     *
     * @param eventId        補償事件 ID
     * @param projectId      專案 ID
     * @param ownerId        本次認領的處理者唯一識別碼（fencing token 之一）
     * @param fencingVersion 本次認領的代數（單調遞增，stale token 將被拒絕）
     * @return true 表示成功取得認領權可執行還原，false 表示重複、他人持有中或 token 已過時
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimRestoreEvent(UUID eventId, UUID projectId, String ownerId, Long fencingVersion) {
        try {
            Optional<CompensationRestoreLog> existing = restoreLogRepository.findById(eventId);
            if (existing.isPresent() && "SUCCESS".equals(existing.get().getStatus())) {
                return false;
            }

            if (existing.isEmpty()) {
                CompensationRestoreLog claim = new CompensationRestoreLog();
                claim.setEventId(eventId);
                claim.setProjectId(projectId);
                claim.setProcessedAt(new Date());
                claim.setStatus("PROCESSING");
                claim.setOwnerId(ownerId);
                claim.setFencingVersion(fencingVersion);
                claim.setLeaseUntil(new Date(System.currentTimeMillis() + restoreLeaseSeconds * 1000L));
                restoreLogRepository.saveAndFlush(claim);
                return true;
            }

            CompensationRestoreLog claim = existing.get();
            if ("PROCESSING".equals(claim.getStatus()) && claim.getLeaseUntil() != null
                    && claim.getLeaseUntil().after(new Date())) {
                // 租約未到期：仍有處理者正在執行，拒絕認領
                return false;
            }
            if ("PROCESSING".equals(claim.getStatus())
                    && claim.getFencingVersion() != null && fencingVersion != null
                    && claim.getFencingVersion() >= fencingVersion) {
                // stale fencing token：拒絕以舊代數接管
                return false;
            }

            Date now = new Date();
            return restoreLogRepository.takeOverClaim(
                    eventId, "PROCESSING", "FAILED", now,
                    new Date(now.getTime() + restoreLeaseSeconds * 1000L),
                    ownerId, fencingVersion) == 1;
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
            return false;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public void restoreMemberSkills(UUID projectId, UUID eventId, Long expectedVersion,
                                     String ownerId, Long fencingVersion, List<Map<String, String>> bindings) {
        // 1. 原子認領事件 (Atomic Idempotency + Fencing Claim)：僅有成功取得 ownership 的實例能執行還原
        boolean claimed = self.claimRestoreEvent(eventId, projectId, ownerId, fencingVersion);
        if (!claimed) {
            log.info("Idempotency Guard: Compensation event {} already processed, claimed by another consumer, or stale token. Skipping.", eventId);
            return;
        }

        // 2. 樂觀防禦比對 (JPA @Version Optimistic Lock Check)
        Project project = projectDataAccess.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (expectedVersion != null && !expectedVersion.equals(project.getVersion())) {
            log.error("COMPENSATION_CONFLICT: Project {} has been updated by another transaction after snapshot! " +
                    "Current DB version = {}, Expected = {}", projectId, project.getVersion(), expectedVersion);
            self.markRestoreFailed(eventId, ownerId, fencingVersion,
                    "Project has newer modifications. Current DB version = " +
                            project.getVersion() + ", Expected = " + expectedVersion);
            throw new CompensationConflictException(
                    "Conflict detected: project has newer modifications. Cannot perform unsafe restore."
            );
        }

        // 3. Commit-time 版本守衛：touch Project 使 JPA @Version 在 commit 時執行 CAS 比對，封閉 TOCTOU 窗口。
        //    此寫入維持「user_project_skill 變更者必須在同交易 bump Project.version」不變式（見 doRebind 註解），
        //    若此區間有並發 rebind 已 commit，commit 時即拋 OptimisticLockException 使整個還原 rollback。
        project.setUpdatedTime(new Date());
        projectDataAccess.save(project);

        // 4. 破壞性操作前再次驗證 fencing token：確認認領紀錄仍由本次 owner+fencingVersion 持有，
        //    若已被更新的租約接管（例如長時間執行中 lease 過期被 reclaimer 接手），則放棄本次還原。
        CompensationRestoreLog current = restoreLogRepository.findById(eventId).orElse(null);
        if (current == null || !ownerId.equals(current.getOwnerId())
                || !fencingVersion.equals(current.getFencingVersion())) {
            log.warn("Fencing token superseded before destructive restore, abort: eventId={}, currentOwner={}, currentFence={}",
                    eventId, current != null ? current.getOwnerId() : "missing",
                    current != null ? current.getFencingVersion() : "missing");
            return;
        }

        try {
            // 5. 刪除該專案目前的技能綁定
            userProjectSkillDataAccess.deleteByProjectId(projectId);

            // 6. 還原
            if (bindings != null) {
                for (Map<String, String> b : bindings) {
                    UUID userId = UUID.fromString(b.get("userId"));
                    UUID skillId = UUID.fromString(b.get("skillId"));
                    UUID levelId = UUID.fromString(b.get("levelId"));

                    Skill skill = skillDataAccess.findById(skillId)
                            .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));
                    SkillLevel skillLevel = skillLevelDataAccess.findById(levelId)
                            .orElseThrow(() -> new IllegalArgumentException("Skill level not found: " + levelId));

                    UserProjectSkill binding = new UserProjectSkill();
                    binding.setUserId(userId);
                    binding.setProject(project);
                    binding.setSkill(skill);
                    binding.setSkillLevel(skillLevel);
                    userProjectSkillDataAccess.save(binding);
                }
            }

            // 7. 還原成功，以獨立交易標記認領日誌為 SUCCESS
            self.markRestoreSuccess(eventId, ownerId, fencingVersion);
        } catch (Exception e) {
            // 8. 發生其他異常，以獨立交易標記認領日誌為 FAILED，供後續重試或人工排查
            self.markRestoreFailed(eventId, ownerId, fencingVersion, e.getMessage());
            throw e;
        }
    }

    /**
     * markRestoreSuccess - 以獨立交易 (REQUIRES_NEW) 標記補償還原認領日誌為 SUCCESS。
     * 獨立 commit 確保成功狀態不因外層交易回滾而遺失；僅接受目前仍由相同
     * ownerId + fencingVersion 持有的紀錄，若已被更新的持有者接管則不覆寫。
     *
     * @param eventId        補償事件 ID
     * @param ownerId        持有者（須與認領紀錄相符）
     * @param fencingVersion 持有代數（須與認領紀錄相符）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRestoreSuccess(UUID eventId, String ownerId, Long fencingVersion) {
        int updated = restoreLogRepository.markRestoreState(
                eventId, ownerId, fencingVersion, "SUCCESS", new Date(), null);
        if (updated == 0) {
            log.warn("markRestoreSuccess skipped (token superseded or log missing): eventId={}", eventId);
        }
    }

    /**
     * markRestoreFailed - 以獨立交易 (REQUIRES_NEW) 標記補償還原認領日誌為 FAILED 並記錄失敗原因。
     * 獨立 commit 確保失敗狀態與錯誤訊息不因外層交易回滾而遺失；僅接受目前仍由相同
     * ownerId + fencingVersion 持有的紀錄，若已被更新的持有者接管則不覆寫。
     *
     * @param eventId        補償事件 ID
     * @param ownerId        持有者（須與認領紀錄相符）
     * @param fencingVersion 持有代數（須與認領紀錄相符）
     * @param reason         失敗原因，寫入 lastError 欄位
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRestoreFailed(UUID eventId, String ownerId, Long fencingVersion, String reason) {
        int updated = restoreLogRepository.markRestoreState(
                eventId, ownerId, fencingVersion, "FAILED", new Date(), reason);
        if (updated == 0) {
            log.warn("markRestoreFailed skipped (token superseded or log missing): eventId={}", eventId);
        }
    }
}
