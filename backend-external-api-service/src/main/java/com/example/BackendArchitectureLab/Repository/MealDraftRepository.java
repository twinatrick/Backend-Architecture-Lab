package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.MealDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MealDraftRepository extends JpaRepository<MealDraft, UUID> {
    Optional<MealDraft> findFirstByOwnerUserIdAndStatusOrderByUpdatedAtDesc(UUID ownerUserId, String status);
    long countByOwnerUserIdAndReceiptIdAndStatusAndExpiresAtGreaterThan(
            UUID ownerUserId, UUID receiptId, String status, Date now);
    List<MealDraft> findTop20ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(String status, Date now);
}
