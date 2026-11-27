package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "meal_record")
@Getter
@Setter
public class MealRecord extends BaseEntity {
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private UUID operationId;
    @Column(nullable = false, length = 64)
    private String requestHash;
    @Version
    private Long version;
    @Column(nullable = false, length = 200)
    private String mealName;
    @Column(nullable = false)
    private Date eatenAt;
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal amount;
    @Column(length = 500)
    private String amountExplanation;
    @Column(length = 200)
    private String restaurantName;
    @Column(length = 500)
    private String locationDescription;
    @Column(length = 50)
    private String floor;
    private Integer rating;
    @Column(length = 4000)
    private String review;
    @Column(name = "receipt_id")
    private UUID receiptId;
    @Column(name = "receipt_item_index")
    private Integer receiptItemIndex;
    @Column(name = "restaurant_location_id")
    private UUID restaurantLocationId;
    @Column(name = "google_maps_url", length = 1024)
    private String googleMapsUrl;
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;
    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;
    private Date deletedAt;
}
