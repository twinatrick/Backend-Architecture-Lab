package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.MealMediaCleanup;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MealMediaCleanupRepository extends JpaRepository<MealMediaCleanup, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<MealMediaCleanup> findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(Date now, Pageable pageable);
    Optional<MealMediaCleanup> findByObjectKey(String objectKey);
}
