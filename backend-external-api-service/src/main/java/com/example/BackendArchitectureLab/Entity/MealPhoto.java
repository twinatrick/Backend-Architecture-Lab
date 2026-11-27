package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "meal_photo")
@Getter
@Setter
public class MealPhoto extends BaseEntity {
    @Column(nullable = false)
    private UUID mealId;
    @Column(nullable = false)
    private UUID operationId;
    @Column(nullable = false, length = 64)
    private String contentHash;
    @Column(nullable = false, length = 300)
    private String objectKey;
    @Column(nullable = false, length = 20)
    private String state;
    @Column(nullable = false, length = 30)
    private String contentType;
    @Column(nullable = false)
    private Long byteSize;
    @Column(nullable = false)
    private Date leaseUntil;
}
