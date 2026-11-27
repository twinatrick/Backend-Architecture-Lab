package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "meal_restaurant_location")
@Getter
@Setter
public class MealRestaurantLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "google_place_id", length = 255)
    private String googlePlaceId;

    @Column(name = "google_maps_url", length = 1024)
    private String googleMapsUrl;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    @Column(name = "floor_text", length = 64)
    private String floorText;

    @Column(name = "normalized_floor", length = 64)
    private String normalizedFloor;

    @Column(name = "location_description", length = 500)
    private String locationDescription;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "business_status", nullable = false, length = 32)
    private String businessStatus;

    @Column(name = "moved_from_location_id")
    private UUID movedFromLocationId;

    @Column(name = "moved_to_location_id")
    private UUID movedToLocationId;

    @Column(name = "last_checked_at")
    private Date lastCheckedAt;

    @Column(name = "next_check_due_at")
    private Date nextCheckDueAt;

    @Column(name = "pending_user_confirmation", nullable = false)
    private boolean pendingUserConfirmation;

    @Column(name = "last_notified_cycle", length = 32)
    private String lastNotifiedCycle;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @Column(name = "updated_at", nullable = false)
    private Date updatedAt;

    @PrePersist
    public void onCreate() {
        Date now = new Date();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = new Date();
    }
}
