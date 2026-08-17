package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.ExternalSyncCommand;
import org.springframework.data.domain.Pageable;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * IExternalSyncCommandDataAccess - 外部同步命令（external_sync_command）資料存取層。
 */
public interface IExternalSyncCommandDataAccess {

    ExternalSyncCommand save(ExternalSyncCommand entity);

    Optional<ExternalSyncCommand> findById(UUID id);

    int claimCommand(UUID id, List<String> claimableStatuses, String processing,
                     Date processingAt, Date leaseUntil);

    List<ExternalSyncCommand> findPendingDue(List<String> pendingStatuses, String processing, Pageable pageable);

    int markSent(UUID id, String sent, String processing, Date sentAt);

    int markFailed(UUID id, String failed, String processing, String errorMessage, Date nextAttemptAt);

    int markDead(UUID id, String dead, String processing, String errorMessage);
}