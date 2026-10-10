package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.DiscordGfSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DiscordGfSession 資料存取介面。
 * 抽象 DiscordGfSessionRepository 供 Service 與監聽器使用。
 */
public interface IDiscordGfSessionDataAccess {

    Optional<DiscordGfSession> findByChannelIdAndUserId(String channelId, String userId);

    DiscordGfSession save(DiscordGfSession session);

    void deleteByChannelIdAndUserId(String channelId, String userId);

    List<DiscordGfSession> findByChannelId(String channelId);

    Optional<DiscordGfSession> findById(UUID id);

    List<DiscordGfSession> findAll();

    void delete(DiscordGfSession session);
}
