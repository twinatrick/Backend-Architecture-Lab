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
@ApiDto(description = "外部整合平台總覽狀態模型")
public class ExternalPlatformStatusVo {
    @ApiField(description = "Discord 平台連線與狀態資訊")
    private DiscordPlatformStatusVo discord;
    @ApiField(description = "LINE 平台連線與狀態資訊")
    private LinePlatformStatusVo line;
    @ApiField(description = "AI 模型供應商連線與狀態資訊")
    private AiPlatformStatusVo ai;
}
