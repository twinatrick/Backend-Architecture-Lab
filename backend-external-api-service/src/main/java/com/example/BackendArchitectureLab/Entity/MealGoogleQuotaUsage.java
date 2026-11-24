package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Entity
@Table(name = "meal_google_quota_usage")
@Getter
@Setter
public class MealGoogleQuotaUsage {
    @Id
    @Column(name = "quota_month", length = 7, nullable = false)
    private String quotaMonth;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "updated_at", nullable = false)
    private Date updatedAt;
}
