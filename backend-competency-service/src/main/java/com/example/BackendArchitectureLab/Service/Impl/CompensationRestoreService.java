package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.ICompensationRestoreLogDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectSkillDataAccess;
import com.example.BackendArchitectureLab.Entity.CompensationRestoreLog;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Entity.UserProjectSkill;
import com.example.BackendArchitectureLab.Exception.CompensationConflictException;
import com.example.BackendArchitectureLab.Service.ICompensationRestoreService;
import com.example.BackendArchitectureLab.Vo.BindingSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * CompensationRestoreService - 補償還原專案成員技能綁定（M-02 自 ProjectUserBindingService 拆分）。
 * 承載 C-01 修正後的完整 restore 流程：原子認領（REQUIRES_NEW）、版本守衛 + 冪等還原比對、
 * 悲觀鎖驗證、破壞性還原、同交易 SUCCESS 標記（Option A）與 reclaim 冪等復原（Option B）。
 */
@Slf4j
@Service
public class CompensationRestoreService implements ICompensationRestoreService {

    @Autowired
    private IProjectDataAccess projectDataAccess;

    @Autowired
    private IUserProjectSkillDataAccess userProjectSkillDataAccess;

    @Autowired
    private ISkillDataAccess skillDataAccess;

    @Autowired
    private ISkillLevelDataAccess skillLevelDataAccess;

    @Autowired
    private ICompensationRestoreLogDataAccess restoreLogRepository;

    @Value("${compensation.restore.lease-seconds:300}")
    private long restoreLeaseSeconds;

    @Autowired
    @Lazy
    private CompensationRestoreService self;

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
            // stale fencing token：FAILED 與 PROCESSING（租約已到期）皆拒絕以舊代數接管，
            // 與 takeOverClaim SQL 的「嚴格更新代數」不變式一致（defense-in-depth）
            if (claim.getFencingVersion() != null && fencingVersion != null
                    && claim.getFencingVersion() >= fencingVersion) {
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

    /**
     * 冪等還原比對（C-01 crash window 復原）：判斷該專案目前的技能綁定是否已等於還原目標
     * （binding snapshot）。若相等，代表先前的 restore 資料已成功 commit（並 bump 了
     * Project.version）只是 SUCCESS 標記遺失，無需再執行破壞性操作，可直接標記 SUCCESS。
     *
     * @param projectId 專案 ID
     * @param bindings  還原目標快照明細（null 視為空集合）
     * @return 目前綁定與目標相等則回傳 true
     */
    private boolean isBindingsAlreadyRestored(UUID projectId, List<BindingSnapshot> bindings) {
        List<UserProjectSkill> current = userProjectSkillDataAccess.findByProjectId(projectId);
        Set<List<UUID>> currentSet = new HashSet<>();
        if (current != null) {
            for (UserProjectSkill row : current) {
                currentSet.add(List.of(
                        row.getUserId(), row.getSkill().getId(), row.getSkillLevel().getId()));
            }
        }
        Set<List<UUID>> targetSet = new HashSet<>();
        if (bindings != null) {
            for (BindingSnapshot b : bindings) {
                targetSet.add(List.of(b.getUserId(), b.getSkillId(), b.getLevelId()));
            }
        }
        return currentSet.equals(targetSet);
    }

    /**
     * 還原前驗證綁定明細（於 DELETE 等破壞性操作之前呼叫）。
     * 檢核每筆綁定的 UUID 欄位皆存在，且 skill / skill level 存在、level 屬於對應 skill，
     * 與 doRebindProjectMemberSkills 的檢核標準一致。任何不符皆拋出 IllegalArgumentException
     * （非重試例外），使 malformed payload 直接轉為 DEAD，避免重複執行 DELETE→INSERT→rollback。
     *
     * @param bindings 歷史綁定快照明細（型別化 DTO）
     */
    private void validateBindingsForRestore(List<BindingSnapshot> bindings) {
        if (bindings == null) {
            return;
        }
        for (BindingSnapshot binding : bindings) {
            UUID userId = binding.getUserId();
            UUID skillId = binding.getSkillId();
            UUID levelId = binding.getLevelId();

            if (userId == null || skillId == null || levelId == null) {
                throw new IllegalArgumentException(
                        "Invalid binding snapshot: userId/skillId/levelId must not be null, got " + binding);
            }

            Skill skill = skillDataAccess.findById(skillId)
                    .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));
            SkillLevel skillLevel = skillLevelDataAccess.findById(levelId)
                    .orElseThrow(() -> new IllegalArgumentException("Skill level not found: " + levelId));

            if (!skillLevel.getSkill().getId().equals(skillId)) {
                throw new IllegalArgumentException(
                        "Skill level " + levelId + " does not belong to skill " + skillId);
            }
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public void restoreMemberSkills(UUID projectId, UUID eventId, Long expectedVersion,
                                     String ownerId, Long fencingVersion, List<BindingSnapshot> bindings) {
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
            // C-01 crash window 復原：先前 restore 資料已 commit 成功但 SUCCESS 標記遺失時
            // （事件被 reclaim，第一次還原已 bump Project.version），目前綁定應等於還原目標。
            // 此種情況直接以同一交易標記 SUCCESS，而非誤判衝突，避免「實際成功、狀態 FAILED/DEAD」。
            if (isBindingsAlreadyRestored(projectId, bindings)) {
                log.info("Compensation reconcile: project {} bindings already match restore target, " +
                        "marking event {} SUCCESS without re-executing restore.", projectId, eventId);
                self.markRestoreSuccess(eventId, ownerId, fencingVersion);
                return;
            }
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

        // 4. 破壞性操作前再次驗證 fencing token：以悲觀寫鎖 (PESSIMISTIC_WRITE) 鎖定認領紀錄，
        //    確認目前仍由本次 owner+fencingVersion 持有，且此鎖持續持有至交易 commit。
        //    其他執行緒的 takeOverClaim CAS 會被此資料列鎖阻塞；待本交易 commit 後其 predicate
        //    （狀態已非 PROCESSING/FAILED 可接管）重新評估即失敗，使舊 token 在 DB 層真正失效。
        CompensationRestoreLog current = restoreLogRepository.findByIdForUpdate(eventId).orElse(null);
        if (current == null || !ownerId.equals(current.getOwnerId())
                || !fencingVersion.equals(current.getFencingVersion())) {
            log.warn("Fencing token superseded before destructive restore, abort: eventId={}, currentOwner={}, currentFence={}",
                    eventId, current != null ? current.getOwnerId() : "missing",
                    current != null ? current.getFencingVersion() : "missing");
            return;
        }

        try {
            // 5. 破壞性操作前驗證 payload：確保 DELETE 前所有綁定皆合法，避免 malformed payload
            //    在 DELETE 之後才拋出 NPE/ClassCastException，白白執行一輪 DELETE→INSERT→rollback
            //    浪費資料庫資源（驗證失敗拋出 IllegalArgumentException，事件直接轉 DEAD）。
            validateBindingsForRestore(bindings);

            // 6. 刪除該專案目前的技能綁定
            userProjectSkillDataAccess.deleteByProjectId(projectId);

            // 7. 還原
            if (bindings != null) {
                for (BindingSnapshot b : bindings) {
                    UUID userId = b.getUserId();
                    UUID skillId = b.getSkillId();
                    UUID levelId = b.getLevelId();

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
        } catch (Exception e) {
            // 8. 發生其他異常：於外層交易 rollback（釋放悲觀鎖）後，以獨立交易標記 FAILED。
            //    不可在此直接以 REQUIRES_NEW 更新認領紀錄——該更新會阻塞於本交易持有的資料列鎖，
            //    而本交易又等待此呼叫回傳，形成死鎖，故改以 afterCompletion 延後執行。
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                final String reason = e.getMessage();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            self.markRestoreFailed(eventId, ownerId, fencingVersion, reason);
                        }
                    }
                });
            } else {
                // 非交易環境（如單元測試直接呼叫）下無同步機制可用，直接標記 FAILED
                self.markRestoreFailed(eventId, ownerId, fencingVersion, e.getMessage());
            }
            throw e;
        }

        // 9. 還原成功：於同一交易內標記 SUCCESS（markRestoreSuccess 已改為加入現行交易而非 REQUIRES_NEW），
        //    與 restore 資料同 commit、同 rollback。若 commit 失敗，SUCCESS 一併回滾，認領紀錄維持
        //    PROCESSING，待租約到期後由 CompensationLeaseReclaimer 回收重試，避免「log=SUCCESS 但實際未還原」；
        //    同交易原子性同時消除「restore 已 commit、SUCCESS 標記遺失」的 crash window（C-01）。
        //    本交易已在第 4 步持有該認領列的 PESSIMISTIC_WRITE 鎖，同交易更新不會死鎖。
        self.markRestoreSuccess(eventId, ownerId, fencingVersion);
    }

    /**
     * markRestoreSuccess - 於現行交易內標記補償還原認領日誌為 SUCCESS。
     * 以 REQUIRED 加入還原交易，與 restore 資料原子 commit/rollback，隨 commit 一起持久化，
     * 消除「restore 已 commit 但 SUCCESS 標記遺失」的 crash window（C-01）；
     * 僅接受目前仍由相同 ownerId + fencingVersion 持有的紀錄，若已被更新的持有者接管則不覆寫。
     * 呼叫前提：restore 交易已取得該認領列的 PESSIMISTIC_WRITE 鎖，同交易更新不會死鎖。
     *
     * @param eventId        補償事件 ID
     * @param ownerId        持有者（須與認領紀錄相符）
     * @param fencingVersion 持有代數（須與認領紀錄相符）
     */
    @Transactional
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