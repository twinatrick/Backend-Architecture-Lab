package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.CompensationRestoreLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Repository
public interface CompensationRestoreLogRepository extends JpaRepository<CompensationRestoreLog, UUID> {

    /**
     * 原子接管補償還原認領（CAS）：僅當事件仍是可認領狀態時才更新為新的 ownerId/fencingVersion。
     * 可認領條件為 FAILED，或 PROCESSING 且租約已到期並由更新的 fencingVersion 接管（拒絕 stale token）。
     * 回傳 1 表示成功接管，0 表示仍被持有者使用中或 token 已過時。
     *
     * @param eventId        補償事件 ID
     * @param processing     PROCESSING 狀態
     * @param failed         FAILED 狀態
     * @param now            目前時間（租約到期比對基準）
     * @param leaseUntil     新租約到期時間
     * @param ownerId        新持有者唯一識別碼
     * @param fencingVersion 新持有者的代數（必須大於現有值才可接管）
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE CompensationRestoreLog r
            SET r.status = :processing, r.processedAt = :now,
                r.ownerId = :ownerId, r.fencingVersion = :fencingVersion, r.leaseUntil = :leaseUntil
            WHERE r.eventId = :eventId
              AND (r.status = :failed
                   OR (r.status = :processing AND r.leaseUntil <= :now
                       AND (r.fencingVersion IS NULL OR r.fencingVersion < :fencingVersion)))
            """)
    int takeOverClaim(@Param("eventId") UUID eventId,
                      @Param("processing") String processing,
                      @Param("failed") String failed,
                      @Param("now") Date now,
                      @Param("leaseUntil") Date leaseUntil,
                      @Param("ownerId") String ownerId,
                      @Param("fencingVersion") Long fencingVersion);

    /**
     * 以獨立交易更新認領日誌的終態（SUCCESS / FAILED），僅接受仍由相同 ownerId + fencingVersion
     * 持有的紀錄；若已被更新的持有者接管（影響 0 列），則不覆寫其結果。
     *
     * @param eventId        補償事件 ID
     * @param ownerId        目前持有者（必須相符）
     * @param fencingVersion 目前持有代數（必須相符）
     * @param status         目標終態
     * @param processedAt    完成時間
     * @param lastError      失敗原因（成功時為 null）
     * @return 更新筆數（0 = 已被他代持有者接管，不應覆寫）
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE CompensationRestoreLog r
            SET r.status = :status, r.processedAt = :processedAt, r.lastError = :lastError
            WHERE r.eventId = :eventId AND r.ownerId = :ownerId AND r.fencingVersion = :fencingVersion
            """)
    int markRestoreState(@Param("eventId") UUID eventId,
                         @Param("ownerId") String ownerId,
                         @Param("fencingVersion") Long fencingVersion,
                         @Param("status") String status,
                         @Param("processedAt") Date processedAt,
                         @Param("lastError") String lastError);
}
