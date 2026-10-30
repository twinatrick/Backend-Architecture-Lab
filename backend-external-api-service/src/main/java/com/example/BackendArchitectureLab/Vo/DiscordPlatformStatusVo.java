package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ApiDto(description = "Discord 平台連線與機器人運行狀態模型")
public class DiscordPlatformStatusVo {
    @ApiField(description = "是否已完成 Discord 平台設定", example = "true")
    private boolean configured;
    @ApiField(description = "虛擬伴侶機器人連線狀態", example = "CONNECTED")
    private String gfStatus;
    @ApiField(description = "語音日記機器人連線狀態", example = "CONNECTED")
    private String diaryStatus;
    @ApiField(description = "虛擬伴侶機器人網關延遲（毫秒）", example = "45")
    private Long gfPing;
    @ApiField(description = "語音日記機器人網關延遲（毫秒）", example = "48")
    private Long diaryPing;
    @ApiField(description = "啟用中之對話工作階段數量", example = "12")
    private long sessionCount;
    @ApiField(description = "啟用中之頻道翻譯訂閱數量", example = "5")
    private long subscriptionCount;
}
