package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "bot_config")
public class BotConfig extends BaseEntity {

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "description")
    private String description;

    @Column(name = "cost_limit_daily")
    private BigDecimal costLimitDaily;

    @Column(name = "cost_alert_at")
    private BigDecimal costAlertAt;
}
