package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IDiscordGfSessionDataAccess;
import com.example.BackendArchitectureLab.Entity.DiscordGfSession;
import com.example.BackendArchitectureLab.Repository.DiscordGfSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * IDiscordGfSessionDataAccess 實作。
 * 委派 DiscordGfSessionRepository 執行資料存取。
 */
@Component
@RequiredArgsConstructor
public class DiscordGfSessionDataAccessImpl implements IDiscordGfSessionDataAccess {

    private final DiscordGfSessionRepository discordGfSessionRepository;

    @Override
    public Optional<DiscordGfSession> findByChannelIdAndUserId(String channelId, String userId) {
        return discordGfSessionRepository.findByChannelIdAndUserId(channelId, userId);
    }

    @Override
    public DiscordGfSession save(DiscordGfSession session) {
        return discordGfSessionRepository.save(session);
    }

    @Override
    @Transactional
    public void deleteByChannelIdAndUserId(String channelId, String userId) {
        discordGfSessionRepository.deleteByChannelIdAndUserId(channelId, userId);
    }

    @Override
    public List<DiscordGfSession> findByChannelId(String channelId) {
        return discordGfSessionRepository.findByChannelId(channelId);
    }

    @Override
    public List<DiscordGfSession> findByGuildId(String guildId) {
        return discordGfSessionRepository.findByGuildId(guildId);
    }

    @Override
    public List<DiscordGfSession> findByGuildIdAndActive(String guildId, Boolean active) {
        return discordGfSessionRepository.findByGuildIdAndActive(guildId, active);
    }

    @Override
    public List<DiscordGfSession> findByActive(Boolean active) {
        return discordGfSessionRepository.findByActive(active);
    }

    @Override
    public Optional<DiscordGfSession> findById(UUID id) {
        return discordGfSessionRepository.findById(id);
    }

    @Override
    public List<DiscordGfSession> findAll() {
        return discordGfSessionRepository.findAll();
    }

    @Override
    public void delete(DiscordGfSession session) {
        discordGfSessionRepository.delete(session);
    }
}
