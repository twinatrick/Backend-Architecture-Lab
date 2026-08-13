package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface CompensationOutboxEventRepository extends JpaRepository<CompensationOutboxEvent, UUID> {

    boolean existsByEventId(UUID eventId);

    /**
     * 依傳遞狀態查詢事件（供監控告警掃描滯留的 DEAD 事件）。
     */
    List<CompensationOutboxEvent> findByDeliveryStatus(String deliveryStatus);

    /**
     * 原子領取（CAS）：事件仍可重試（PENDING/FAILED 且已達下次重試時間），
     * 或 PROCESSING 且租約已過期（crash recovery），才將其標記為 PROCESSING，
     * 同時遞增 attemptCount 並寫入處理時間與新租約。回傳 1 表示取得處理權、0 表示他人處理中或未到期。
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE CompensationOutboxEvent e
            SET e.deliveryStatus = :processing,
                e.attemptCount = e.attemptCount + 1,
                e.processingAt = :processingAt,
                e.leaseUntil = :leaseUntil
            WHERE e.id = :id
              AND (
                    (e.deliveryStatus <> :processing
                     AND e.deliveryStatus IN :claimableStatuses
                     AND (e.nextAttemptAt IS NULL OR e.nextAttemptAt <= CURRENT_TIMESTAMP))
                    OR
                    (e.deliveryStatus = :processing
                     AND e.leaseUntil IS NOT NULL AND e.leaseUntil <= CURRENT_TIMESTAMP)
                  )
            """)
    int claimEvent(@Param("id") UUID id,
                   @Param("claimableStatuses") List<String> claimableStatuses,
                   @Param("processing") String processing,
                    @Param("processingAt") Date processingAt,
                    @Param("leaseUntil") Date leaseUntil);

    /**
     * 查詢尚未送達且已到期的事件（PENDING/FAILED 已到期，或 PROCESSING 租約已過期可回收），
     * 依建立時間排序。
     */
    @Query("""
            SELECT e FROM CompensationOutboxEvent e
            WHERE e.deliveryStatus IN :pendingStatuses
              AND (
                    (e.deliveryStatus <> :processing
                     AND (e.nextAttemptAt IS NULL OR e.nextAttemptAt <= CURRENT_TIMESTAMP))
                    OR
                    (e.deliveryStatus = :processing
                     AND e.leaseUntil IS NOT NULL AND e.leaseUntil <= CURRENT_TIMESTAMP)
                  )
            ORDER BY e.createdTime ASC
            """)
    List<CompensationOutboxEvent> findPendingDue(@Param("pendingStatuses") List<String> pendingStatuses,
                                                 @Param("processing") String processing,
                                                 Pageable pageable);

    /**
     * 原子標記已送達（僅 PROCESSING 狀態可轉換；SENT + 清除租約）。
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE CompensationOutboxEvent e
            SET e.deliveryStatus = :sent, e.sentAt = :sentAt, e.leaseUntil = NULL
            WHERE e.id = :id AND e.deliveryStatus = :processing
            """)
    int markSent(@Param("id") UUID id,
                 @Param("sent") String sent,
                 @Param("processing") String processing,
                 @Param("sentAt") Date sentAt);

    /**
     * 原子標記失敗（僅 PROCESSING 狀態可轉換；FAILED + 記錄錯誤 + 排定下次重試 + 清除租約）。
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE CompensationOutboxEvent e
            SET e.deliveryStatus = :failed, e.errorMessage = :errorMessage,
                e.nextAttemptAt = :nextAttemptAt, e.leaseUntil = NULL
            WHERE e.id = :id AND e.deliveryStatus = :processing
            """)
    int markFailed(@Param("id") UUID id,
                   @Param("failed") String failed,
                   @Param("processing") String processing,
                   @Param("errorMessage") String errorMessage,
                   @Param("nextAttemptAt") Date nextAttemptAt);

    /**
     * 原子標記死亡（僅 PROCESSING 狀態可轉換；DEAD + 記錄錯誤 + 清除租約）。
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE CompensationOutboxEvent e
            SET e.deliveryStatus = :dead, e.errorMessage = :errorMessage, e.leaseUntil = NULL
            WHERE e.id = :id AND e.deliveryStatus = :processing
            """)
    int markDead(@Param("id") UUID id,
                 @Param("dead") String dead,
                 @Param("processing") String processing,
                 @Param("errorMessage") String errorMessage);
}