package com.example.BackendArchitectureLab.Vo.Search;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.Vo.Common.BaseSearchQuery;
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
@ApiDto(description = "Discord 女友會話搜尋查詢參數")
public class DiscordGfSessionSearchQuery extends BaseSearchQuery {

    @ApiField(description = "關鍵字（模糊搜尋 guildId、channelId、userId、prompt、gfName）", example = "小愛")
    private String keyword;

    @ApiField(description = "伺服器 ID（模糊查詢）", example = "123456789012345678")
    private String guildId;

    @ApiField(description = "頻道 ID（精確或模糊查詢）", example = "123456789012345678")
    private String channelId;

    @ApiField(description = "使用者 ID（精確或模糊查詢）", example = "123456789012345678")
    private String userId;

    @ApiField(description = "啟用狀態", example = "true")
    private Boolean active;

    @ApiField(description = "女友稱呼（模糊查詢）", example = "小愛")
    private String gfName;

    @ApiField(description = "語言設定（如 zh、en）", example = "zh")
    private String language;

    @ApiField(description = "是否啟用語音", example = "true")
    private Boolean voiceEnabled;
}
