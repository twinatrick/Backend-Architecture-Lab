package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "meal_line_binding_code")
@Getter
@Setter
public class MealLineBindingCode {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "binding_code", nullable = false, length = 32)
    private String bindingCode;

    @Column(name = "expires_at", nullable = false)
    private Date expiresAt;

    @Column(nullable = false)
    private boolean consumed;

    @Column(name = "consumed_at")
    private Date consumedAt;

    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = new Date();
        }
    }
}
