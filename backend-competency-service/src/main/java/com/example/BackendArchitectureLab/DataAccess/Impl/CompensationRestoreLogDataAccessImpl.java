package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.ICompensationRestoreLogDataAccess;
import com.example.BackendArchitectureLab.Entity.CompensationRestoreLog;
import com.example.BackendArchitectureLab.Repository.CompensationRestoreLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * CompensationRestoreLogDataAccessImpl - ICompensationRestoreLogDataAccess 實作（薄委派 CompensationRestoreLogRepository）。
 */
@Component
@RequiredArgsConstructor
public class CompensationRestoreLogDataAccessImpl implements ICompensationRestoreLogDataAccess {

    private final CompensationRestoreLogRepository repository;

    @Override
    public Optional<CompensationRestoreLog> findById(UUID eventId) {
        return repository.findById(eventId);
    }

    @Override
    public boolean existsById(UUID eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public CompensationRestoreLog save(CompensationRestoreLog entity) {
        return repository.save(entity);
    }

    @Override
    public CompensationRestoreLog saveAndFlush(CompensationRestoreLog entity) {
        return repository.saveAndFlush(entity);
    }

    @Override
    public Optional<CompensationRestoreLog> findByIdForUpdate(UUID eventId) {
        return repository.findByIdForUpdate(eventId);
    }

    @Override
    public int takeOverClaim(UUID eventId, String processing, String failed, Date now,
                             Date leaseUntil, String ownerId, Long fencingVersion) {
        return repository.takeOverClaim(eventId, processing, failed, now, leaseUntil, ownerId, fencingVersion);
    }

    @Override
    public int markRestoreState(UUID eventId, String ownerId, Long fencingVersion,
                                String status, Date processedAt, String lastError) {
        return repository.markRestoreState(eventId, ownerId, fencingVersion, status, processedAt, lastError);
    }
}