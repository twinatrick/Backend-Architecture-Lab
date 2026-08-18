package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.ICompensationEventLogDataAccess;
import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Repository.CompensationEventLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CompensationEventLogDataAccessImpl - ICompensationEventLogDataAccess 實作（薄委派 CompensationEventLogRepository）。
 */
@Component
@RequiredArgsConstructor
public class CompensationEventLogDataAccessImpl implements ICompensationEventLogDataAccess {

    private final CompensationEventLogRepository repository;

    @Override
    public CompensationEventLog save(CompensationEventLog entity) {
        return repository.save(entity);
    }

    @Override
    public CompensationEventLog saveAndFlush(CompensationEventLog entity) {
        return repository.saveAndFlush(entity);
    }

    @Override
    public Optional<CompensationEventLog> findByEventId(UUID eventId) {
        return repository.findByEventId(eventId);
    }

    @Override
    public List<CompensationEventLog> findTop20ByStatusOrderByUpdatedTimeDesc(String status) {
        return repository.findTop20ByStatusOrderByUpdatedTimeDesc(status);
    }

    @Override
    public List<CompensationEventLog> findTop20ByStatusAndFailedAtAfter(String status, Date failedAt) {
        return repository.findTop20ByStatusAndFailedAtAfter(status, failedAt);
    }

    @Override
    public List<CompensationEventLog> findTop50ByStatusAndLeaseUntilBeforeOrderByLeaseUntilAsc(String status, Date leaseUntil) {
        return repository.findTop50ByStatusAndLeaseUntilBeforeOrderByLeaseUntilAsc(status, leaseUntil);
    }

    @Override
    public List<CompensationEventLog> findTop50ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(String status, Date nextAttemptAt) {
        return repository.findTop50ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(status, nextAttemptAt);
    }

    @Override
    public int retryClaim(UUID eventId, String processing, String failed, String ownerId,
                          Date processingAt, Date leaseUntil, Date now) {
        return repository.retryClaim(eventId, processing, failed, ownerId, processingAt, leaseUntil, now);
    }

    @Override
    public int reclaimLease(UUID eventId, String processing, Date now, String ownerId,
                            Date processingAt, Date leaseUntil) {
        return repository.reclaimLease(eventId, processing, now, ownerId, processingAt, leaseUntil);
    }

    @Override
    public int markState(UUID eventId, String ownerId, Long fencingVersion, String processing,
                         String status, Date processedAt, Date failedAt, String lastError, Date nextAttemptAt) {
        return repository.markState(eventId, ownerId, fencingVersion, processing,
                status, processedAt, failedAt, lastError, nextAttemptAt);
    }
}
