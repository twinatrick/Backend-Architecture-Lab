package com.example.BackendArchitectureLab.Vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscordSubscriptionVo {
    private UUID id;
    private String guildId;
    private String channelId;
    private String botType;
    private String webhookUrl;
    private String webhookId;
    private String language;
    private Date createdTime;
    private Date updatedTime;
}
