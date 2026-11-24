package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.MealReceiptPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MealReceiptPhotoRepository extends JpaRepository<MealReceiptPhoto, UUID> {
    Optional<MealReceiptPhoto> findByReceiptIdAndOwnerUserIdAndIdempotencyKey(
            UUID receiptId, UUID ownerUserId, String idempotencyKey);
    Optional<MealReceiptPhoto> findByIdAndReceiptIdAndOwnerUserId(
            UUID id, UUID receiptId, UUID ownerUserId);
    List<MealReceiptPhoto> findByReceiptIdAndOwnerUserIdOrderBySortOrderAscCreatedAtAsc(
            UUID receiptId, UUID ownerUserId);
    long countByReceiptId(UUID receiptId);
    boolean existsByObjectKey(String objectKey);

    @Transactional
    void deleteByReceiptId(UUID receiptId);
}
