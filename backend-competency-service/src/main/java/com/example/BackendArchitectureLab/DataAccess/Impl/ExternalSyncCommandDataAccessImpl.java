package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IExternalSyncCommandDataAccess;
import com.example.BackendArchitectureLab.Entity.ExternalSyncCommand;
import com.example.BackendArchitectureLab.Repository.ExternalSyncCommandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ExternalSyncCommandDataAccessImpl - IExternalSyncCommandDataAccess 實作（薄委派 ExternalSyncCommandRepository）。
 */
@Component
public class ExternalSyncCommandDataAccessImpl implements IExternalSyncCommandDataAccess {

    @Autowired
    private ExternalSyncCommandRepository repository;

    @Override
    public ExternalSyncCommand save(ExternalSyncCommand entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<ExternalSyncCommand> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public int claimCommand(UUID id, List<String> claimableStatuses, String processing,
                            String ownerId, Date processingAt, Date leaseUntil) {
        return repository.claimCommand(id, claimableStatuses, processing, ownerId, processingAt, leaseUntil);
    }

    @Override
    public List<ExternalSyncCommand> findPendingDue(List<String> pendingStatuses, String processing, Pageable pageable) {
        return repository.findPendingDue(pendingStatuses, processing, pageable);
    }

    @Override
    public int markSent(UUID id, String ownerId, Long fencingVersion, String sent, String processing, Date sentAt) {
        return repository.markSent(id, ownerId, fencingVersion, sent, processing, sentAt);
    }

    @Override
    public int markFailed(UUID id, String ownerId, Long fencingVersion, String failed, String processing, String errorMessage, Date nextAttemptAt) {
        return repository.markFailed(id, ownerId, fencingVersion, failed, processing, errorMessage, nextAttemptAt);
    }

    @Override
    public int markDead(UUID id, String ownerId, Long fencingVersion, String dead, String processing, String errorMessage) {
        return repository.markDead(id, ownerId, fencingVersion, dead, processing, errorMessage);
    }
}