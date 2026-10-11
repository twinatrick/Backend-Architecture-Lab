package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IDiscordSubscriptionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.specification.DiscordSubscriptionSpecification;
import com.example.BackendArchitectureLab.Entity.DiscordSubscription;
import com.example.BackendArchitectureLab.Repository.DiscordSubscriptionRepository;
import com.example.BackendArchitectureLab.Vo.Search.DiscordSubscriptionSearchQuery;
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
 * IDiscordSubscriptionDataAccess 實作。
 * 委派 DiscordSubscriptionRepository 執行資料存取。
 */
@Component
@RequiredArgsConstructor
public class DiscordSubscriptionDataAccessImpl implements IDiscordSubscriptionDataAccess {

    private final DiscordSubscriptionRepository discordSubscriptionRepository;

    @Override
    public Optional<DiscordSubscription> findByGuildIdAndBotType(String guildId, String botType) {
        return discordSubscriptionRepository.findByGuildIdAndBotType(guildId, botType);
    }

    @Override
    @Transactional
    public void deleteByGuildIdAndBotType(String guildId, String botType) {
        discordSubscriptionRepository.deleteByGuildIdAndBotType(guildId, botType);
    }

    @Override
    public List<DiscordSubscription> findByGuildId(String guildId) {
        return discordSubscriptionRepository.findByGuildId(guildId);
    }

    @Override
    public List<DiscordSubscription> findAll() {
        return discordSubscriptionRepository.findAll();
    }

    @Override
    public Page<DiscordSubscription> findAllPaged(Pageable pageable) {
        return discordSubscriptionRepository.findAll(pageable);
    }

    @Override
    public Page<DiscordSubscription> searchSubscriptions(DiscordSubscriptionSearchQuery query) {
        Sort sort = Sort.by(
                "asc".equalsIgnoreCase(query.getNormalizedSortDir())
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC,
                query.getSortBy()
        );
        PageRequest pageRequest = PageRequest.of(query.getPage(), query.getSize(), sort);
        return discordSubscriptionRepository.findAll(DiscordSubscriptionSpecification.buildSpecification(query), pageRequest);
    }

    @Override
    public Optional<DiscordSubscription> findById(UUID id) {
        return discordSubscriptionRepository.findById(id);
    }

    @Override
    public DiscordSubscription save(DiscordSubscription subscription) {
        return discordSubscriptionRepository.save(subscription);
    }

    @Override
    public void deleteById(UUID id) {
        discordSubscriptionRepository.deleteById(id);
    }

    @Override
    public void delete(DiscordSubscription subscription) {
        discordSubscriptionRepository.delete(subscription);
    }
}
