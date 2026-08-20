package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import org.springframework.data.domain.Pageable;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ICompensationOutboxEventDataAccess - 補償 Outbox 事件（compensation_outbox_event）資料存取層。
 */
public interface ICompensationOutboxEventDataAccess {

    CompensationOutboxEvent save(CompensationOutboxEvent entity);

    Optional<CompensationOutboxEvent> findById(UUID id);

    boolean existsByEventId(UUID eventId);

    List<CompensationOutboxEvent> findTop20ByDeliveryStatusOrderByUpdatedTimeDesc(String deliveryStatus);

    int claimEvent(UUID id, List<String> claimableStatuses, String processing,
                   String ownerId, Date processingAt, Date leaseUntil);

    List<CompensationOutboxEvent> findPendingDue(List<String> pendingStatuses, String processing, Pageable pageable);

    int markSent(UUID id, String ownerId, Long fencingVersion, String sent, String processing, Date sentAt);

    int markFailed(UUID id, String ownerId, Long fencingVersion, String failed, String processing, String errorMessage, Date nextAttemptAt);

    int markDead(UUID id, String ownerId, Long fencingVersion, String dead, String processing, String errorMessage);

    int markFailedByOwner(UUID id, String ownerId, String failed, String processing, String errorMessage, Date nextAttemptAt);

    int markDeadByOwner(UUID id, String ownerId, String dead, String processing, String errorMessage);
}