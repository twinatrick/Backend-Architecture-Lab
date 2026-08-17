package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.ICompensationRestoreLogDataAccess;
import com.example.BackendArchitectureLab.Service.ICompensationRestoreStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.UUID;

/**
 * CompensationRestoreStateService - 補償還原認領日誌的狀態標記實作（M-02 拆分）。
 * markRestoreSuccess 以 REQUIRED 加入還原交易（與 restore 資料原子 commit/rollback，C-01）；
 * markRestoreFailed 以 REQUIRES_NEW 獨立 commit，確保失敗狀態不因外層回滾而遺失。
 */
@Slf4j
@Service
public class CompensationRestoreStateService implements ICompensationRestoreStateService {

    @Autowired
    private ICompensationRestoreLogDataAccess restoreLogRepository;

    @Autowired
    @Lazy
    private CompensationRestoreStateService self;

    @Override
    @Transactional
    public void markRestoreSuccess(UUID eventId, String ownerId, Long fencingVersion) {
        int updated = restoreLogRepository.markRestoreState(
                eventId, ownerId, fencingVersion, "SUCCESS", new Date(), null);
        if (updated == 0) {
            log.warn("markRestoreSuccess skipped (token superseded or log missing): eventId={}", eventId);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRestoreFailed(UUID eventId, String ownerId, Long fencingVersion, String reason) {
        int updated = restoreLogRepository.markRestoreState(
                eventId, ownerId, fencingVersion, "FAILED", new Date(), reason);
        if (updated == 0) {
            log.warn("markRestoreFailed skipped (token superseded or log missing): eventId={}", eventId);
        }
    }

    @Override
    public void scheduleMarkRestoreFailed(UUID eventId, String ownerId, Long fencingVersion, Exception e) {
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
    }
}
