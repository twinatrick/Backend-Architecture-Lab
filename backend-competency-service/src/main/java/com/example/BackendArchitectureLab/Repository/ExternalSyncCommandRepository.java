package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.ExternalSyncCommand;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface ExternalSyncCommandRepository extends JpaRepository<ExternalSyncCommand, UUID> {

    /**
     * 原子領取（CAS）：命令仍可重試（PENDING/FAILED 且已達下次重試時間），
     * 或 PROCESSING 且租約已過期（crash recovery），才將其標記為 PROCESSING，
     * 同時遞增 attemptCount、更新 ownerId 與 fencingVersion（COALESCE + 1）並寫入處理時間與新租約。
     * 回傳 1 表示取得處理權、0 表示他人處理中或未到期。
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE ExternalSyncCommand c
            SET c.deliveryStatus = :processing,
                c.attemptCount = c.attemptCount + 1,
                c.ownerId = :ownerId,
                c.fencingVersion = COALESCE(c.fencingVersion, 0) + 1,
                c.processingAt = :processingAt,
                c.leaseUntil = :leaseUntil,
                c.nextAttemptAt = NULL
            WHERE c.id = :id
              AND (
                    (c.deliveryStatus <> :processing
                     AND c.deliveryStatus IN :claimableStatuses
                     AND (c.nextAttemptAt IS NULL OR c.nextAttemptAt <= CURRENT_TIMESTAMP))
                    OR
                    (c.deliveryStatus = :processing
                     AND c.leaseUntil IS NOT NULL AND c.leaseUntil <= CURRENT_TIMESTAMP)
                  )
            """)
    int claimCommand(@Param("id") UUID id,
                     @Param("claimableStatuses") List<String> claimableStatuses,
                     @Param("processing") String processing,
                     @Param("ownerId") String ownerId,
                     @Param("processingAt") Date processingAt,
                     @Param("leaseUntil") Date leaseUntil);

    /**
     * 查詢尚未同步且已到期的命令（PENDING/FAILED 已到期，或 PROCESSING 租約已過期可回收），依建立時間排序。
     */
    @Query("""
            SELECT c FROM ExternalSyncCommand c
            WHERE c.deliveryStatus IN :pendingStatuses
              AND (
                    (c.deliveryStatus <> :processing
                     AND (c.nextAttemptAt IS NULL OR c.nextAttemptAt <= CURRENT_TIMESTAMP))
                    OR
                    (c.deliveryStatus = :processing
                     AND c.leaseUntil IS NOT NULL AND c.leaseUntil <= CURRENT_TIMESTAMP)
                  )
            ORDER BY c.createdTime ASC
            """)
    List<ExternalSyncCommand> findPendingDue(@Param("pendingStatuses") List<String> pendingStatuses,
                                             @Param("processing") String processing,
                                             Pageable pageable);

    /**
     * 原子標記已同步（僅 PROCESSING 狀態且持有一致的 ownerId 與 fencingVersion 可轉換；SENT + 清除租約）。
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE ExternalSyncCommand c
            SET c.deliveryStatus = :sent, c.sentAt = :sentAt, c.leaseUntil = NULL
            WHERE c.id = :id
              AND c.ownerId = :ownerId
              AND c.fencingVersion = :fencingVersion
              AND c.deliveryStatus = :processing
            """)
    int markSent(@Param("id") UUID id,
                 @Param("ownerId") String ownerId,
                 @Param("fencingVersion") Long fencingVersion,
                 @Param("sent") String sent,
                 @Param("processing") String processing,
                 @Param("sentAt") Date sentAt);

    /**
     * 原子標記失敗（僅 PROCESSING 狀態且持有一致的 ownerId 與 fencingVersion 可轉換；FAILED + 記錄錯誤 + 排定下次重試 + 清除租約）。
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE ExternalSyncCommand c
            SET c.deliveryStatus = :failed, c.errorMessage = :errorMessage,
                c.nextAttemptAt = :nextAttemptAt, c.leaseUntil = NULL
            WHERE c.id = :id
              AND c.ownerId = :ownerId
              AND c.fencingVersion = :fencingVersion
              AND c.deliveryStatus = :processing
            """)
    int markFailed(@Param("id") UUID id,
                   @Param("ownerId") String ownerId,
                   @Param("fencingVersion") Long fencingVersion,
                   @Param("failed") String failed,
                   @Param("processing") String processing,
                   @Param("errorMessage") String errorMessage,
                   @Param("nextAttemptAt") Date nextAttemptAt);

    /**
     * 原子標記死亡（僅 PROCESSING 狀態且持有一致的 ownerId 與 fencingVersion 可轉換；DEAD + 記錄錯誤 + 清除租約）。
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE ExternalSyncCommand c
            SET c.deliveryStatus = :dead, c.errorMessage = :errorMessage, c.leaseUntil = NULL
            WHERE c.id = :id
              AND c.ownerId = :ownerId
              AND c.fencingVersion = :fencingVersion
              AND c.deliveryStatus = :processing
            """)
    int markDead(@Param("id") UUID id,
                 @Param("ownerId") String ownerId,
                 @Param("fencingVersion") Long fencingVersion,
                 @Param("dead") String dead,
                 @Param("processing") String processing,
                 @Param("errorMessage") String errorMessage);
}