package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.DiscordSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiscordSubscriptionRepository extends JpaRepository<DiscordSubscription, UUID> {
    Optional<DiscordSubscription> findByGuildIdAndBotType(String guildId, String botType);
    void deleteByGuildIdAndBotType(String guildId, String botType);
    List<DiscordSubscription> findByGuildId(String guildId);
}
