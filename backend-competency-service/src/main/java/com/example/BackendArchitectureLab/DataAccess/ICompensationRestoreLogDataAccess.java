package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.CompensationRestoreLog;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * ICompensationRestoreLogDataAccess - 補償還原認領紀錄（compensation_restore_log）資料存取層。
 */
public interface ICompensationRestoreLogDataAccess {

    Optional<CompensationRestoreLog> findById(UUID eventId);

    boolean existsById(UUID eventId);

    CompensationRestoreLog save(CompensationRestoreLog entity);

    CompensationRestoreLog saveAndFlush(CompensationRestoreLog entity);

    Optional<CompensationRestoreLog> findByIdForUpdate(UUID eventId);

    int takeOverClaim(UUID eventId, String processing, String failed, Date now,
                      Date leaseUntil, String ownerId, Long fencingVersion);

    int markRestoreState(UUID eventId, String ownerId, Long fencingVersion,
                         String status, Date processedAt, String lastError);
}