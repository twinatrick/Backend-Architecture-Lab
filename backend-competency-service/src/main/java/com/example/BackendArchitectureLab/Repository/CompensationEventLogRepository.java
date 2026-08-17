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
     * 依狀態查詢前 20 筆事件，依更新時間降序排列（避免 DEAD 事件全表掃描）。
     */
    List<CompensationEventLog> findTop20ByStatusOrderByUpdatedTimeDesc(String status);

    /**
     * 查詢指定時間之後失敗的滯留事件（僅掃描近期失敗，避免每輪全量掃描歷史 FAILED；LIMIT 20）。
     */
    List<CompensationEventLog> findTop20ByStatusAndFailedAtAfter(String status, Date failedAt);

    /**
     * 原子重試領取（CAS）：僅當事件先前處理失敗（FAILED）、且已達下次可重試時間
     * （nextAttemptAt 為空或已過）時，才標記為 PROCESSING、更新租約並累計嘗試次數。
     * 同時以新 ownerId 佔有並將 fencingVersion 單調遞增（+1），供下游補償執行做 fencing token 驗證。
     * nextAttemptAt 守衛確保 Kafka 每 1 秒的 redelivery 不會繞過 markFailed 的線性退避。
     * 回傳 1 表示取得處理權、0 表示他人正在重試、尚未到期或狀態不允許。
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE CompensationEventLog e
            SET e.status = :processing, e.attemptCount = e.attemptCount + 1,
                e.ownerId = :ownerId, e.fencingVersion = COALESCE(e.fencingVersion, 0) + 1,
                e.processingAt = :processingAt, e.leaseUntil = :leaseUntil,
                e.nextAttemptAt = NULL
            WHERE e.eventId = :eventId AND e.status = :failed
              AND (e.nextAttemptAt IS NULL OR e.nextAttemptAt <= :now)
            """)
    int retryClaim(@Param("eventId") UUID eventId,
                   @Param("processing") String processing,
                   @Param("failed") String failed,
                   @Param("ownerId") String ownerId,
                   @Param("processingAt") Date processingAt,
                   @Param("leaseUntil") Date leaseUntil,
                   @Param("now") Date now);

    /**
     * 原子重新認領（CAS）：僅當事件仍在 PROCESSING 且租約已到期（處理者 crash 或逾時）時，
     * 才累計嘗試次數、更新處理時間與新租約，並以新 ownerId 佔有、fencingVersion 單調遞增（+1）。
     * 回傳 1 表示取得處理權、0 表示他人正在處理。
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE CompensationEventLog e
            SET e.attemptCount = e.attemptCount + 1,
                e.ownerId = :ownerId, e.fencingVersion = COALESCE(e.fencingVersion, 0) + 1,
                e.processingAt = :processingAt, e.leaseUntil = :leaseUntil
            WHERE e.eventId = :eventId AND e.status = :processing AND e.leaseUntil <= :now
            """)
    int reclaimLease(@Param("eventId") UUID eventId,
                     @Param("processing") String processing,
                     @Param("now") Date now,
                     @Param("ownerId") String ownerId,
                     @Param("processingAt") Date processingAt,
                     @Param("leaseUntil") Date leaseUntil);

    /**
     * 查詢租約已過期的 PROCESSING 事件（處理者 crash 後留下的滯留紀錄），
     * 依租約到期時間遞增排序、限 50 筆，供過期租約回收排程接手。
     */
    List<CompensationEventLog> findTop50ByStatusAndLeaseUntilBeforeOrderByLeaseUntilAsc(String status, Date leaseUntil);

    /**
     * 原子標記處理終態（CAS）：僅當事件目前仍由相同 ownerId + fencingVersion 持有且仍為 PROCESSING
     * 時，才標記為最終狀態（PROCESSED/FAILED/DEAD）並寫入結果欄位。若租約已被其他實例以
     * reclaimLease/retryClaim 接管（stale token），回傳 0 且不覆寫新持有者的紀錄，
     * 確保「只有最新一代持有者能標記結果」。processedAt / failedAt / lastError / nextAttemptAt
     * 依狀態語意可傳 null（例如 PROCESSED 不需 failedAt 與 nextAttemptAt，皆會被清空）。
     *
     * @return 1 表示成功寫入；0 表示 stale token（租約已被接管）或狀態不符
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE CompensationEventLog e
            SET e.status = :status,
                e.processedAt = :processedAt,
                e.failedAt = :failedAt,
                e.lastError = :lastError,
                e.nextAttemptAt = :nextAttemptAt
            WHERE e.eventId = :eventId
              AND e.ownerId = :ownerId
              AND e.fencingVersion = :fencingVersion
              AND e.status = :processing
            """)
    int markState(@Param("eventId") UUID eventId,
                  @Param("ownerId") String ownerId,
                  @Param("fencingVersion") Long fencingVersion,
                  @Param("processing") String processing,
                  @Param("status") String status,
                  @Param("processedAt") Date processedAt,
                  @Param("failedAt") Date failedAt,
                  @Param("lastError") String lastError,
                  @Param("nextAttemptAt") Date nextAttemptAt);

    /**
     * 查詢已達下次重試時間（nextAttemptAt <= 現在）的 FAILED 事件，依重試時間遞增排序、限 50 筆，
     * 供過期租約回收排程重新領取重試（transient failure 但 Kafka 已無 redelivery 的事件）。
     */
    List<CompensationEventLog> findTop50ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(String status, Date nextAttemptAt);
}
