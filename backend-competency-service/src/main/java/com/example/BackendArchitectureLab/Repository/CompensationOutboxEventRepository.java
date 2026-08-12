package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompensationOutboxEventRepository extends JpaRepository<CompensationOutboxEvent, UUID> {

    List<CompensationOutboxEvent> findTop20BySentFalseOrderByCreatedTimeAsc();

    boolean existsByEventId(UUID eventId);
}