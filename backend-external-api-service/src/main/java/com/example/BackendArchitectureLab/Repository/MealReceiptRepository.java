package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.MealReceipt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MealReceiptRepository extends JpaRepository<MealReceipt, UUID> {
    Optional<MealReceipt> findByOwnerUserIdAndIdempotencyKey(UUID ownerUserId, String idempotencyKey);
    Optional<MealReceipt> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MealReceipt r where r.id = :id and r.ownerUserId = :ownerUserId")
    Optional<MealReceipt> lockOwned(@Param("id") UUID id, @Param("ownerUserId") UUID ownerUserId);
}
