package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.MealLineNotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MealLineNotificationOutboxRepository extends JpaRepository<MealLineNotificationOutbox, UUID> {
    Optional<MealLineNotificationOutbox> findByDedupKey(String dedupKey);
    List<MealLineNotificationOutbox> findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            List<String> statuses, Date now);
}
