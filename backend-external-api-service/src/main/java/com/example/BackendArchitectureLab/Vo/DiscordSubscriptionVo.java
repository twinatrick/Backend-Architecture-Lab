package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
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
@ApiDto(description = "Discord 頻道自動翻譯訂閱設定模型")
public class DiscordSubscriptionVo {
    @ApiField(description = "訂閱設定唯一識別碼", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private UUID id;
    @ApiField(description = "Discord 伺服器（Guild）識別碼", example = "123456789012345678")
    private String guildId;
    @ApiField(description = "Discord 頻道識別碼", example = "987654321098765432")
    private String channelId;
    @ApiField(description = "機器人類型", example = "TRANSLATE")
    private String botType;
    @ApiField(description = "Discord Webhook 網址", example = "https://discord.com/api/webhooks/123/abc")
    private String webhookUrl;
    @ApiField(description = "Discord Webhook 識別碼", example = "1234567890")
    private String webhookId;
    @ApiField(description = "目標翻譯語言代碼", example = "zh-TW")
    private String language;
    @ApiField(description = "建立時間", example = "2026-04-12T08:00:00Z")
    private Date createdTime;
    @ApiField(description = "最後更新時間", example = "2026-04-12T09:00:00Z")
    private Date updatedTime;
}
