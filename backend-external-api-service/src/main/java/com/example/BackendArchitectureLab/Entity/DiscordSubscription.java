package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "discord_subscription")
public class DiscordSubscription extends BaseEntity {

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @Column(name = "bot_type", nullable = false)
    private String botType;

    @Column(name = "webhook_url")
    private String webhookUrl;

    @Column(name = "webhook_id")
    private String webhookId;

    public DiscordSubscription(String guildId, String channelId, String botType) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.botType = botType;
    }
}
