package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.MealGoogleQuotaUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MealGoogleQuotaUsageRepository extends JpaRepository<MealGoogleQuotaUsage, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from MealGoogleQuotaUsage q where q.quotaMonth = :quotaMonth")
    Optional<MealGoogleQuotaUsage> lockByMonth(@Param("quotaMonth") String quotaMonth);
}
