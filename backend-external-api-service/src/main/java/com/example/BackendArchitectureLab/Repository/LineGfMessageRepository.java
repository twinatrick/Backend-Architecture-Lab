package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.LineGfMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LineGfMessageRepository extends JpaRepository<LineGfMessage, UUID> {
    List<LineGfMessage> findBySessionIdOrderByCreatedTimeDesc(UUID sessionId, Pageable pageable);
    List<LineGfMessage> findBySessionIdOrderByCreatedTimeAsc(UUID sessionId);
    long countBySessionId(UUID sessionId);
    void deleteBySessionId(UUID sessionId);
}
