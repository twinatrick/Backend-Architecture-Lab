package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.DiscordSubscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DiscordSubscription 資料存取介面。
 * 抽象 DiscordSubscriptionRepository 供 Service 與監聽器使用。
 */
public interface IDiscordSubscriptionDataAccess {

    Optional<DiscordSubscription> findByGuildIdAndBotType(String guildId, String botType);

    void deleteByGuildIdAndBotType(String guildId, String botType);

    List<DiscordSubscription> findByGuildId(String guildId);

    List<DiscordSubscription> findAll();

    Optional<DiscordSubscription> findById(UUID id);

    DiscordSubscription save(DiscordSubscription subscription);

    void deleteById(UUID id);

    void delete(DiscordSubscription subscription);
}
