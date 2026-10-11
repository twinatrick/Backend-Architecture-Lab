package com.example.BackendArchitectureLab.Vo.Search;

import com.example.BackendArchitectureLab.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Discord 女友會話分頁搜尋查詢參數。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Discord 女友會話搜尋查詢參數")
public class DiscordGfSessionSearchQuery extends BaseSearchQuery {

    @Schema(description = "關鍵字（模糊搜尋 guildId、channelId、userId、prompt、gfName）")
    private String keyword;

    @Schema(description = "伺服器 ID（模糊查詢）")
    private String guildId;

    @Schema(description = "頻道 ID（精確或模糊查詢）")
    private String channelId;

    @Schema(description = "使用者 ID（精確或模糊查詢）")
    private String userId;

    @Schema(description = "啟用狀態")
    private Boolean active;

    @Schema(description = "女友稱呼（模糊查詢）")
    private String gfName;

    @Schema(description = "語言設定（如 zh、en）")
    private String language;

    @Schema(description = "是否啟用語音")
    private Boolean voiceEnabled;
}
