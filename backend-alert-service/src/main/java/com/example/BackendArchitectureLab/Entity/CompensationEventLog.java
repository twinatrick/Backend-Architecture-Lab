package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * CompensationEventLog - 已接收的補償事件紀錄，
 * 以 eventId 唯一鍵保證事件冪等處理（重複事件直接忽略）。
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

    @Column(name = "received_at", nullable = false)
    private Date receivedAt;
}