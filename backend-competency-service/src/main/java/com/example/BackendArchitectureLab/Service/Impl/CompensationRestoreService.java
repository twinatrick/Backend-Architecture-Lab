package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectSkillDataAccess;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.UserProjectSkill;
import com.example.BackendArchitectureLab.Exception.CompensationConflictException;
import com.example.BackendArchitectureLab.Service.ICompensationRestoreClaimService;
import com.example.BackendArchitectureLab.Service.ICompensationRestoreService;
import com.example.BackendArchitectureLab.Service.ICompensationRestoreStateService;
import com.example.BackendArchitectureLab.Service.ICompensationRestoreValidatorService;
import com.example.BackendArchitectureLab.Vo.BindingSnapshot;
import com.example.BackendArchitectureLab.Vo.CompensationRestoreResultVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * CompensationRestoreService - 補償還原專案成員技能綁定的流程編排（M-02 拆分）。
 * 承載 restore 全流程編排：認領（ClaimService，REQUIRES_NEW）、版本守衛 + 冪等比對（ValidatorService）、
 * 悲觀鎖驗證（ClaimService）、破壞性還原、同交易 SUCCESS 標記（StateService，C-01）。
 * 認領、驗證、狀態標記三大職責已拆分至 ICompensationRestoreClaimService /
 * ICompensationRestoreValidatorService / ICompensationRestoreStateService。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationRestoreService implements ICompensationRestoreService {

    private final IProjectDataAccess projectDataAccess;
    private final IUserProjectSkillDataAccess userProjectSkillDataAccess;
    private final ICompensationRestoreClaimService claimService;
    private final ICompensationRestoreValidatorService validatorService;
    private final ICompensationRestoreStateService stateService;

    @Override
    @Transactional
    @CacheEvict(value = "projectSkills", key = "#projectId")
    public CompensationRestoreResultVo restoreMemberSkills(UUID projectId, UUID eventId, Long expectedVersion,
                                                           String ownerId, Long fencingVersion, List<BindingSnapshot> bindings) {
        if (expectedVersion == null) {
            throw new IllegalArgumentException("expectedVersion must not be null for competency restore");
        }

        // 1. 原子認領事件 (Atomic Idempotency + Fencing Claim)：僅有成功取得 ownership 的實例能執行還原
        boolean claimed = claimService.claimRestoreEvent(eventId, projectId, ownerId, fencingVersion, bindings);
        if (!claimed) {
            log.info("Idempotency Guard: Compensation event {} already processed, claimed by another consumer, or stale token. Skipping.", eventId);
            return new CompensationRestoreResultVo(true, "Already processed or claimed by another instance", projectId, eventId);
        }

        try {
            // 2. 樂觀防禦比對 (JPA @Version Optimistic Lock Check)
            Project project = projectDataAccess.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

            // 2.5 破壞性操作前驗證並解析 payload（方案 B 含 membership 驗證）。
            //     確保後續 reconcile 比對與 DELETE 前所有綁定皆合法：malformed 或非成員的 payload
            //     在此拋 IllegalArgumentException（事件直接轉 DEAD / FAILED），避免在 List.of(...)
            //     reconcile 比對或 DELETE 之後才拋出 NPE，白白執行一輪 DELETE→INSERT→rollback（P3 修復）。
            List<UserProjectSkill> resolvedBindings = validatorService.resolveBindingsForRestore(projectId, project, bindings);

            if (!expectedVersion.equals(project.getVersion())) {
                // C-01 crash window 復原：先前 restore 資料已 commit 成功 but SUCCESS 標記遺失時
                // （事件被 reclaim，第一次還原已 bump Project.version），目前綁定應等於還原目標。
                // 此種情況直接以同一交易標記 SUCCESS，而非誤判衝突，避免「實際成功、狀態 FAILED/DEAD」。
                if (validatorService.isBindingsAlreadyRestored(projectId, bindings)) {
                    log.info("Compensation reconcile: project {} bindings already match restore target, " +
                            "marking event {} SUCCESS without re-executing restore.", projectId, eventId);
                    stateService.markRestoreSuccess(eventId, ownerId, fencingVersion);
                    return new CompensationRestoreResultVo(true, "Already restored", projectId, eventId);
                }
                log.error("COMPENSATION_CONFLICT: Project {} has been updated by another transaction after snapshot! " +
                        "Current DB version = {}, Expected = {}", projectId, project.getVersion(), expectedVersion);
                throw new CompensationConflictException(
                        "Conflict detected: project has newer modifications. Cannot perform unsafe restore."
                );
            }

            // 3. 破壞性操作前（及任何寫入前）再次驗證 fencing token：以悲觀寫鎖 (PESSIMISTIC_WRITE)
            //    鎖定認領紀錄，確認目前仍由本次 owner + fencingVersion 持有，且此鎖持續持有至交易 commit。
            //    其他執行緒的 takeOverClaim CAS 會被此資料列鎖阻塞；待本交易 commit 後其 predicate
            //    （狀態已非 PROCESSING/FAILED 可接管）重新評估即失敗，使舊 token 在 DB 層真正失效。
            //    此驗證必須排在 touch Project 之前：若租約已被新代數接管而早退 return 時，
            //    交易尚未有 dirty 寫入，不會白 bump Project.version（避免下一持有者因版本不符而誤判衝突）。
            if (!claimService.verifyFencingHeld(eventId, ownerId, fencingVersion)) {
                return new CompensationRestoreResultVo(false, "Fencing verification failed", projectId, eventId);
            }

            // 4. Commit-time 版本守衛：touch Project 使 JPA @Version 在 commit 時執行 CAS 比對，封閉 TOCTOU 窗口。
            //    此寫入維持「user_project_skill 變更者必須在同交易 bump Project.version」不變式（見 doRebind 註解），
            //    若此區間有並發 rebind 已 commit，commit 時即拋 OptimisticLockException 使整個還原 rollback。
            project.setUpdatedTime(new Date());
            projectDataAccess.save(project);

            // 5. 刪除該專案目前的技能綁定
            userProjectSkillDataAccess.deleteByProjectId(projectId);

            // 6. 還原（直接重用 resolveBindingsForRestore 已解析並驗證的 entity，不再重查 skill/level，DRY）
            for (UserProjectSkill binding : resolvedBindings) {
                userProjectSkillDataAccess.save(binding);
            }

            // 7. 還原成功：於同一交易內標記 SUCCESS（StateService.markRestoreSuccess 以 REQUIRED 加入現行交易），
            //    與 restore 資料同 commit、同 rollback。若 commit 失敗，SUCCESS 一併回滾，認領紀錄維持
            //    PROCESSING，待租約到期後由 CompensationLeaseReclaimer 回收重試，避免「log=SUCCESS 但實際未還原」；
            //    同交易原子性同時消除「restore 已 commit、SUCCESS 標記遺失」的 crash window（C-01）。
            //    本交易已在第 3 步持有該認領列的 PESSIMISTIC_WRITE 鎖，同交易更新不會死鎖。
            stateService.markRestoreSuccess(eventId, ownerId, fencingVersion);
            return new CompensationRestoreResultVo(true, "Successfully restored", projectId, eventId);
        } catch (Exception e) {
            // 8. 發生任何異常（專案不存在、payload 驗證失敗、版本衝突、DB 寫入失敗等）：
            //    於外層交易 rollback（釋放悲觀鎖）後，以獨立交易標記 FAILED 並寫入錯誤原因。
            //    不可在此直接以 REQUIRES_NEW 更新認領紀錄——該更新若在持悲觀鎖階段執行會造成死鎖，
            //    故統一改以 afterCompletion 延後執行，確保認領紀錄進入 FAILED 終態而不被無限 reclaim。
            stateService.scheduleMarkRestoreFailed(eventId, ownerId, fencingVersion, e);
            throw e;
        }
    }
}
