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
@Table(name = "meal_draft")
@Getter
@Setter
public class MealDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "line_user_id", nullable = false, length = 100)
    private String lineUserId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "meal_name", length = 200)
    private String mealName;

    @Column(name = "dined_at")
    private Date dinedAt;

    @Column(name = "paid_amount", precision = 8, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "amount_explanation", length = 500)
    private String amountExplanation;

    @Column(name = "receipt_id")
    private UUID receiptId;

    @Column(name = "receipt_item_index")
    private Integer receiptItemIndex;

    @Column(name = "restaurant_location_id")
    private UUID restaurantLocationId;

    @Column(name = "restaurant_name", length = 200)
    private String restaurantName;

    @Column(name = "floor_text", length = 64)
    private String floorText;

    @Column(name = "location_description", length = 500)
    private String locationDescription;

    @Column(name = "google_maps_url", length = 1024)
    private String googleMapsUrl;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    private Integer rating;

    @Column(name = "review_text", length = 4000)
    private String reviewText;

    @Column(name = "pending_photo_keys_json", length = 4000)
    private String pendingPhotoKeysJson;

    @Column(name = "confirmed_meal_id")
    private UUID confirmedMealId;

    @Column(name = "expires_at", nullable = false)
    private Date expiresAt;

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
