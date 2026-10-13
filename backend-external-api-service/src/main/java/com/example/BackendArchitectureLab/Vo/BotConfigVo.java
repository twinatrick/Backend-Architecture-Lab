package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiDto(description = "機器人設定資料傳輸物件")
public class BotConfigVo {

    @ApiField(description = "設定 ID", format = "uuid", example = "00000000-0000-0000-0000-000000000001")
    private UUID id;

    @ApiField(description = "所屬平台 (如 DISCORD, LINE)", example = "DISCORD")
    private String platform;

    @ApiField(description = "設定鍵", example = "DISCORD_BOT_TOKEN")
    private String configKey;

    @ApiField(description = "設定值", example = "sample-token-value")
    private String configValue;

    @ApiField(description = "設定描述說明", example = "主要 Discord 機器人連線金鑰")
    private String description;

    @ApiField(description = "建立時間", example = "2026-10-01T00:00:00.000Z")
    private Date createdTime;

    @ApiField(description = "更新時間", example = "2026-10-01T00:00:00.000Z")
    private Date updatedTime;
}
