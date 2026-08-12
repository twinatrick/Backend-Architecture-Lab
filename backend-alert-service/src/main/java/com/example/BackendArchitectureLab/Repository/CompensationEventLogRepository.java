package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompensationEventLogRepository extends JpaRepository<CompensationEventLog, UUID> {

    boolean existsByEventId(UUID eventId);
}