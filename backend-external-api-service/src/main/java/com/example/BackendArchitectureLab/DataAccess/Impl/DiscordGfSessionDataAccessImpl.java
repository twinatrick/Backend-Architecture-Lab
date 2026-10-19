package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IDiscordGfSessionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.specification.DiscordGfSessionSpecification;
import com.example.BackendArchitectureLab.Entity.DiscordGfSession;
import com.example.BackendArchitectureLab.Repository.DiscordGfSessionRepository;
import com.example.BackendArchitectureLab.Dto.Query.DiscordGfSessionSearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    public Page<DiscordGfSession> findAllPaged(Pageable pageable) {
        return discordGfSessionRepository.findAll(pageable);
    }

    @Override
    public Page<DiscordGfSession> searchSessions(DiscordGfSessionSearchQuery query) {
        Sort sort = Sort.by(
                "asc".equalsIgnoreCase(query.getNormalizedSortDir())
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC,
                query.getSortBy()
        );
        PageRequest pageRequest = PageRequest.of(query.getPage(), query.getSize(), sort);
        return discordGfSessionRepository.findAll(DiscordGfSessionSpecification.buildSpecification(query), pageRequest);
    }

    @Override
    public void delete(DiscordGfSession session) {
        discordGfSessionRepository.delete(session);
    }
}
