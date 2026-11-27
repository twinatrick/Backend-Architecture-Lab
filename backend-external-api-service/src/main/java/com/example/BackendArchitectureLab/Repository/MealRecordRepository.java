package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.MealRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MealRecordRepository extends JpaRepository<MealRecord, UUID>, JpaSpecificationExecutor<MealRecord> {
    Optional<MealRecord> findByUserIdAndOperationId(UUID userId, UUID operationId);
    Optional<MealRecord> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
    long countByUserIdAndReceiptIdAndDeletedAtIsNull(UUID userId, UUID receiptId);
    List<MealRecord> findByUserIdAndReceiptIdAndDeletedAtIsNullOrderByCreatedTimeAsc(UUID userId, UUID receiptId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select meal from MealRecord meal where meal.id = :id and meal.userId = :userId and meal.deletedAt is null")
    Optional<MealRecord> lockOwned(@Param("id") UUID id, @Param("userId") UUID userId);
}
