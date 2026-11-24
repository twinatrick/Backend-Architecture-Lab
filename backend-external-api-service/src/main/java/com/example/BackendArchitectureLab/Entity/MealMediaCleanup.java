package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Entity
@Table(name = "meal_media_cleanup")
@Getter
@Setter
public class MealMediaCleanup extends BaseEntity {
    @Column(nullable = false, length = 300)
    private String objectKey;
    @Column(nullable = false)
    private Date nextAttemptAt;
    @Column(nullable = false)
    private int attempts;
    @Column(length = 100)
    private String lastError;
}
