package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.DataAccess.IBotConfigDataAccess;
import com.example.BackendArchitectureLab.Entity.BotConfig;
import com.example.BackendArchitectureLab.Repository.BotConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * IBotConfigDataAccess 實作。
 * 委派 BotConfigRepository 執行資料存取。
 */
@Component
public class BotConfigDataAccessImpl implements IBotConfigDataAccess {

    @Autowired
    private BotConfigRepository botConfigRepository;

    @Override
    public List<BotConfig> findAll() {
        return botConfigRepository.findAll();
    }

    @Override
    public Optional<BotConfig> findById(UUID id) {
        return botConfigRepository.findById(id);
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
