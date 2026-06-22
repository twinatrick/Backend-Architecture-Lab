package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.BotConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BotConfigRepository extends JpaRepository<BotConfig, UUID> {
    Optional<BotConfig> findByPlatformAndConfigKey(String platform, String configKey);
}
