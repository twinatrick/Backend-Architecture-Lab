package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompensationEventLogRepository extends JpaRepository<CompensationEventLog, UUID> {

    Optional<CompensationEventLog> findByEventId(UUID eventId);

    /**
     * 依處理狀態查詢事件（供監控告警掃描滯留的 FAILED 事件）。
     */
    List<CompensationEventLog> findByStatus(String status);

    /**
     * 原子重試領取（CAS）：僅當事件先前處理失敗（FAILED）時，才標記為 PROCESSING、更新租約並累計嘗試次數。
     * 回傳 1 表示取得處理權、0 表示他人正在重試或狀態不允許。
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE CompensationEventLog e
            SET e.status = :processing, e.attemptCount = e.attemptCount + 1,
                e.processingAt = :processingAt, e.leaseUntil = :leaseUntil
            WHERE e.eventId = :eventId AND e.status = :failed
            """)
    int retryClaim(@Param("eventId") UUID eventId,
                   @Param("processing") String processing,
                   @Param("failed") String failed,
                   @Param("processingAt") Date processingAt,
                   @Param("leaseUntil") Date leaseUntil);

    /**
     * 原子重新認領（CAS）：僅當事件仍在 PROCESSING 且租約已到期（處理者 crash 或逾時）時，
     * 才累計嘗試次數、更新處理時間與新租約。回傳 1 表示取得處理權、0 表示他人正在處理。
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE CompensationEventLog e
            SET e.attemptCount = e.attemptCount + 1,
                e.processingAt = :processingAt, e.leaseUntil = :leaseUntil
            WHERE e.eventId = :eventId AND e.status = :processing AND e.leaseUntil <= :now
            """)
    int reclaimLease(@Param("eventId") UUID eventId,
                     @Param("processing") String processing,
                     @Param("now") Date now,
                     @Param("processingAt") Date processingAt,
                     @Param("leaseUntil") Date leaseUntil);
}