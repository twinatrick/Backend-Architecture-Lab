package com.example.BackendArchitectureLab.Dto.Query;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.Dto.Query.BaseSearchQuery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Discord 頻道訂閱分頁搜尋查詢參數。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ApiDto(description = "Discord 頻道訂閱搜尋查詢參數")
public class DiscordSubscriptionSearchQuery extends BaseSearchQuery {

    @ApiField(description = "關鍵字（模糊搜尋 guildId、channelId、botType、webhookUrl、webhookId）", example = "DIARY")
    private String keyword;

    @ApiField(description = "伺服器 ID（模糊查詢）", example = "123456789012345678")
    private String guildId;

    @ApiField(description = "頻道 ID（精確或模糊查詢）", example = "123456789012345678")
    private String channelId;

    @ApiField(description = "機器人類型（如 DIARY）", example = "DIARY")
    private String botType;

    @ApiField(description = "語言設定（如 zh、en）", example = "zh")
    private String language;
}
