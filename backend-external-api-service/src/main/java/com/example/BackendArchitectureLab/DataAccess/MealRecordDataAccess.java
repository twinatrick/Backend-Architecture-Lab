package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.MealRecord;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Repository.MealRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MealRecordDataAccess {
    private final MealRecordRepository repository;

    public MealRecord owned(UUID userId, UUID id, boolean lock) {
        return (lock ? repository.lockOwned(id, userId) : repository.findByIdAndUserIdAndDeletedAtIsNull(id, userId))
                .orElseThrow(() -> new AppException("MEAL_NOT_FOUND", "餐點不存在或不屬於目前使用者", 404));
    }

    public Optional<MealRecord> byOperation(UUID userId, UUID operationId) {
        return repository.findByUserIdAndOperationId(userId, operationId);
    }

    public long countActiveByReceipt(UUID userId, UUID receiptId) {
        return repository.countByUserIdAndReceiptIdAndDeletedAtIsNull(userId, receiptId);
    }

    public List<MealRecord> findActiveByReceipt(UUID userId, UUID receiptId) {
        return repository.findByUserIdAndReceiptIdAndDeletedAtIsNullOrderByCreatedTimeAsc(userId, receiptId);
    }

    public MealRecord save(MealRecord entity) {
        return repository.saveAndFlush(entity);
    }

    public Page<MealRecord> search(Specification<MealRecord> specification, Pageable pageable) {
        return repository.findAll(specification, pageable);
    }
}
