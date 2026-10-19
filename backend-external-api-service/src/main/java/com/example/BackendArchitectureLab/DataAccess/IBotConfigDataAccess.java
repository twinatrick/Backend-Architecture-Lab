package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.BotConfig;
import com.example.BackendArchitectureLab.Dto.Query.BotConfigSearchQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * BotConfig 資料存取介面。
 * 抽象 BotConfigRepository 供 Service 層使用。
 */
public interface IBotConfigDataAccess {

    List<BotConfig> findAll();

    Page<BotConfig> findAllPaged(Pageable pageable);

    Page<BotConfig> searchBotConfigs(BotConfigSearchQuery query);

    Optional<BotConfig> findById(UUID id);

    List<BotConfig> findAllById(List<UUID> ids);

    BotConfig save(BotConfig entity);

    boolean existsById(UUID id);

    void deleteById(UUID id);
}
