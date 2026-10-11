package com.example.BackendArchitectureLab.Vo.Search;

import com.example.BackendArchitectureLab.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Discord 頻道訂閱搜尋查詢參數")
public class DiscordSubscriptionSearchQuery extends BaseSearchQuery {

    @Schema(description = "關鍵字（模糊搜尋 guildId、channelId、botType、webhookUrl、webhookId）")
    private String keyword;

    @Schema(description = "伺服器 ID（模糊查詢）")
    private String guildId;

    @Schema(description = "頻道 ID（精確或模糊查詢）")
    private String channelId;

    @Schema(description = "機器人類型（如 DIARY）")
    private String botType;

    @Schema(description = "語言設定（如 zh、en）")
    private String language;
}
