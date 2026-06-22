package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.DiscordGfSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiscordGfSessionRepository extends JpaRepository<DiscordGfSession, UUID> {
    Optional<DiscordGfSession> findByChannelIdAndUserId(String channelId, String userId);
    void deleteByChannelIdAndUserId(String channelId, String userId);
    List<DiscordGfSession> findByChannelId(String channelId);
}
