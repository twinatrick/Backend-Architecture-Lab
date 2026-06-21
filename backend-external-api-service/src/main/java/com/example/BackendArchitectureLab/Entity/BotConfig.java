package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "bot_config")
public class BotConfig extends BaseEntity {

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "description")
    private String description;

    @Column(name = "cost_limit_daily")
    private java.math.BigDecimal costLimitDaily;

    @Column(name = "cost_alert_at")
    private java.math.BigDecimal costAlertAt;
}
