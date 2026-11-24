package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Entity
@Table(name = "meal_line_event_dedup")
@Getter
@Setter
public class MealLineEventDedup {
    @Id
    @Column(name = "event_key", length = 128, nullable = false)
    private String eventKey;

    @Column(name = "line_user_id", nullable = false, length = 100)
    private String lineUserId;

    @Column(name = "processed_at", nullable = false)
    private Date processedAt;
}
