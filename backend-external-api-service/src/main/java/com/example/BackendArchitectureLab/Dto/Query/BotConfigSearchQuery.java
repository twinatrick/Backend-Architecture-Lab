package com.example.BackendArchitectureLab.Dto.Query;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.Dto.Query.BaseSearchQuery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Bot 設定分頁搜尋查詢參數。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ApiDto(description = "Bot 設定搜尋查詢參數")
public class BotConfigSearchQuery extends BaseSearchQuery {

    @ApiField(description = "關鍵字（模糊搜尋 platform、configKey、description、configValue）", example = "DISCORD")
    private String keyword;

    @ApiField(description = "平台代碼（模糊查詢，如 DISCORD、LINE）", example = "DISCORD")
    private String platform;

    @ApiField(description = "設定鍵（模糊查詢）", example = "DISCORD_TOKEN")
    private String configKey;

    @ApiField(description = "描述說明（模糊查詢）", example = "Bot 連線設定")
    private String description;
}
