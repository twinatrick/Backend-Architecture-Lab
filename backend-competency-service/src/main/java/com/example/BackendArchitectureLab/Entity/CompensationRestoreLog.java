package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * CompensationRestoreLog - 補償還原事件的去重紀錄日誌。
 * 以 eventId (即 Kafka 補償事件 ID) 作為主鍵進行應用級等冪防重。
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "compensation_restore_log")
public class CompensationRestoreLog {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "processed_at", nullable = false)
    private Date processedAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "last_error", length = 1024)
    private String lastError;
}
