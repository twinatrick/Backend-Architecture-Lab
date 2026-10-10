package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.DiscordGfMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DiscordGfMessageRepository extends JpaRepository<DiscordGfMessage, UUID> {
    List<DiscordGfMessage> findTop20BySessionIdOrderByCreatedTimeDesc(UUID sessionId);
    List<DiscordGfMessage> findBySessionIdOrderByCreatedTimeAsc(UUID sessionId);
    long countBySessionId(UUID sessionId);
    void deleteBySessionId(UUID sessionId);
}
