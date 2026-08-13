package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * CompensationEventLog - 已接收的補償事件處理紀錄，
 * 以 eventId 唯一鍵 + 原子插入作為並發領取（claim）的防護，
 * status 記錄處理狀態（PROCESSING/PROCESSED/FAILED）；
 * leaseUntil 為處理租約到期時間，逾期的 PROCESSING 可被其他實例重新認領（crash recovery）。
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "compensation_event_log")
public class CompensationEventLog extends BaseEntity {

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "received_at", nullable = false)
    private Date receivedAt;

    @Column(name = "processing_at")
    private Date processingAt;

    @Column(name = "lease_until")
    private Date leaseUntil;

    @Column(name = "processed_at")
    private Date processedAt;

    @Column(name = "failed_at")
    private Date failedAt;

    @Column(name = "last_alerted_at")
    private Date lastAlertedAt;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(name = "fencing_version")
    private Long fencingVersion;

    @Lob
    @Column(name = "payload")
    private String payload;
}