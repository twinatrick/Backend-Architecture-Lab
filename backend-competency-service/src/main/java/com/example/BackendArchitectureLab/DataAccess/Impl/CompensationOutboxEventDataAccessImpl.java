package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.ICompensationOutboxEventDataAccess;
import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.Repository.CompensationOutboxEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CompensationOutboxEventDataAccessImpl - ICompensationOutboxEventDataAccess 實作（薄委派 CompensationOutboxEventRepository）。
 */
@Component
public class CompensationOutboxEventDataAccessImpl implements ICompensationOutboxEventDataAccess {

    @Autowired
    private CompensationOutboxEventRepository repository;

    @Override
    public CompensationOutboxEvent save(CompensationOutboxEvent entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<CompensationOutboxEvent> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public boolean existsByEventId(UUID eventId) {
        return repository.existsByEventId(eventId);
    }

    @Override
    public List<CompensationOutboxEvent> findTop20ByDeliveryStatusOrderByUpdatedTimeDesc(String deliveryStatus) {
        return repository.findTop20ByDeliveryStatusOrderByUpdatedTimeDesc(deliveryStatus);
    }

    @Override
    public int claimEvent(UUID id, List<String> claimableStatuses, String processing,
                          Date processingAt, Date leaseUntil) {
        return repository.claimEvent(id, claimableStatuses, processing, processingAt, leaseUntil);
    }

    @Override
    public List<CompensationOutboxEvent> findPendingDue(List<String> pendingStatuses, String processing, Pageable pageable) {
        return repository.findPendingDue(pendingStatuses, processing, pageable);
    }

    @Override
    public int markSent(UUID id, String sent, String processing, Date sentAt) {
        return repository.markSent(id, sent, processing, sentAt);
    }

    @Override
    public int markFailed(UUID id, String failed, String processing, String errorMessage, Date nextAttemptAt) {
        return repository.markFailed(id, failed, processing, errorMessage, nextAttemptAt);
    }

    @Override
    public int markDead(UUID id, String dead, String processing, String errorMessage) {
        return repository.markDead(id, dead, processing, errorMessage);
    }
}