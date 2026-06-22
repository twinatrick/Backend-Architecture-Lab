package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Entity.BotConfig;
import com.example.BackendArchitectureLab.Repository.BotConfigRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BotConfigLoader {

    @Autowired
    private BotConfigRepository botConfigRepository;

    private final Map<String, String> configCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadAll() {
        botConfigRepository.findAll().forEach(cfg ->
                configCache.put(cfg.getPlatform() + ":" + cfg.getConfigKey(), cfg.getConfigValue()));
    }

    public String get(String platform, String key) {
        return configCache.get(platform + ":" + key);
    }

    public void refresh(String platform, String key) {
        botConfigRepository.findByPlatformAndConfigKey(platform, key).ifPresent(cfg ->
                configCache.put(platform + ":" + key, cfg.getConfigValue()));
    }

    public void evict(String platform, String key) {
        configCache.remove(platform + ":" + key);
    }
}
