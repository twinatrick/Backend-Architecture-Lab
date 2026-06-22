package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "discord_gf_session", uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = {"channel_id", "user_id"}))
public class DiscordGfSession extends BaseEntity {

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "active")
    private Boolean active = false;

    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "voice_enabled")
    private Boolean voiceEnabled = false;

    @Column(name = "voice_sample_key")
    private String voiceSampleKey;

    @Column(name = "voice_sample_text", columnDefinition = "TEXT")
    private String voiceSampleText;

    @Column(name = "gf_name")
    private String gfName;

    @Column(name = "gf_avatar_url", columnDefinition = "TEXT")
    private String gfAvatarUrl;

    @Column(name = "conversation_history", columnDefinition = "TEXT")
    private String conversationHistory;
}
