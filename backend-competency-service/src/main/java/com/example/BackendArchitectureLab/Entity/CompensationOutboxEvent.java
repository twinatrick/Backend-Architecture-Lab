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
 * CompensationOutboxEvent - 交易補償事件的 Outbox 記錄，
 * 與業務交易同 commit 寫入，由排程批次發送至 Kafka，
 * 確保「資料庫交易」與「事件發佈」之間的可靠性。
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "compensation_outbox_event")
public class CompensationOutboxEvent extends BaseEntity {

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "sent", nullable = false)
    private boolean sent;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "sent_at")
    private Date sentAt;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;
}