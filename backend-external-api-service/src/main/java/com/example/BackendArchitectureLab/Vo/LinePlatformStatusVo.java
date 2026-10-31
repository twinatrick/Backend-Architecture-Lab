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
@ApiDto(description = "LINE 平台設定與連線狀態模型")
public class LinePlatformStatusVo {
    @ApiField(description = "是否已完成 LINE 平台設定", example = "true")
    private boolean configured;
    @ApiField(description = "是否已設定 Channel Access Token", example = "true")
    private boolean channelTokenConfigured;
    @ApiField(description = "是否已設定 Channel Secret", example = "true")
    private boolean channelSecretConfigured;
    @ApiField(description = "啟用中之對話工作階段數量", example = "8")
    private long sessionCount;
}
