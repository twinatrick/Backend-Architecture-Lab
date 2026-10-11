package com.example.BackendArchitectureLab.Vo.Search;

import com.example.BackendArchitectureLab.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Bot 設定搜尋查詢參數")
public class BotConfigSearchQuery extends BaseSearchQuery {

    @Schema(description = "關鍵字（模糊搜尋 platform、configKey、description、configValue）")
    private String keyword;

    @Schema(description = "平台代碼（模糊查詢，如 DISCORD、LINE）")
    private String platform;

    @Schema(description = "設定鍵（模糊查詢）")
    private String configKey;

    @Schema(description = "描述說明（模糊查詢）")
    private String description;
}
