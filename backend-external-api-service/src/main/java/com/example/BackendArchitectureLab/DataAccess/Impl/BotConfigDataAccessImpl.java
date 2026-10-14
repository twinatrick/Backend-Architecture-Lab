package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IBotConfigDataAccess;
import com.example.BackendArchitectureLab.DataAccess.specification.BotConfigSpecification;
import com.example.BackendArchitectureLab.Entity.BotConfig;
import com.example.BackendArchitectureLab.Repository.BotConfigRepository;
import com.example.BackendArchitectureLab.Vo.Search.BotConfigSearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * IBotConfigDataAccess 實作。
 * 委派 BotConfigRepository 執行資料存取。
 */
@Component
@RequiredArgsConstructor
public class BotConfigDataAccessImpl implements IBotConfigDataAccess {

    private final BotConfigRepository botConfigRepository;

    @Override
    public List<BotConfig> findAll() {
        return botConfigRepository.findAll();
    }

    @Override
    public Page<BotConfig> findAllPaged(Pageable pageable) {
        return botConfigRepository.findAll(pageable);
    }

    @Override
    public Page<BotConfig> searchBotConfigs(BotConfigSearchQuery query) {
        Sort sort = Sort.by(
                "asc".equalsIgnoreCase(query.getNormalizedSortDir())
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC,
                query.getSortBy()
        );
        PageRequest pageRequest = PageRequest.of(query.getPage(), query.getSize(), sort);
        return botConfigRepository.findAll(BotConfigSpecification.buildSpecification(query), pageRequest);
    }

    @Override
    public Optional<BotConfig> findById(UUID id) {
        return botConfigRepository.findById(id);
    }

    @Override
    public List<BotConfig> findAllById(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return botConfigRepository.findAllById(ids);
    }

    @Override
    public BotConfig save(BotConfig entity) {
        return botConfigRepository.save(entity);
    }

    @Override
    public boolean existsById(UUID id) {
        return botConfigRepository.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        botConfigRepository.deleteById(id);
    }
}
