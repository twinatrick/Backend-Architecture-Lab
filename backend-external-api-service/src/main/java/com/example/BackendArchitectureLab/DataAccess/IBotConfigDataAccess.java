package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.BotConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * BotConfig 資料存取介面。
 * 抽象 BotConfigRepository 供 Service 層使用。
 */
public interface IBotConfigDataAccess {

    List<BotConfig> findAll();

    Optional<BotConfig> findById(UUID id);

    BotConfig save(BotConfig entity);

    boolean existsById(UUID id);

    void deleteById(UUID id);
}
