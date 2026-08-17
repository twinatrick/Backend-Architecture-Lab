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
 * ExternalSyncCommand - 專案成員技能綁定的外部系統同步命令（durable command）。
 * <p>
 * 與業務交易同 commit 寫入（Transactional Outbox 模式），由 {@code Timer/ExternalSyncWorker}
 * 定期執行：透過 claim CAS / lease / 指數退避確保「DB 已 commit 的外部同步」一定被可靠觸發，
 * 消除 fixme：本地 commit 後 JVM crash 導致外部同步永不執行的 crash window。
 * 超過最大重試次數轉為 DEAD 時，才觸發補償閉環（COMPENSATION_REQUIRED）。
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "external_sync_command")
public class ExternalSyncCommand extends BaseEntity {

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "delivery_status", nullable = false, length = 20)
    private String deliveryStatus;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "processing_at")
    private Date processingAt;

    @Column(name = "lease_until")
    private Date leaseUntil;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Date nextAttemptAt;

    @Column(name = "sent_at")
    private Date sentAt;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;
}