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
@ApiDto(description = "AI 平台連線與設定狀態模型")
public class AiPlatformStatusVo {
    @ApiField(description = "是否已完成平台設定", example = "true")
    private boolean configured;
    @ApiField(description = "AI 服務供應商名稱", example = "GEMINI")
    private String provider;
    @ApiField(description = "是否已設定 API 金鑰", example = "true")
    private boolean apiKeyConfigured;
    @ApiField(description = "目前啟用之模型名稱", example = "gemini-2.5-flash")
    private String modelName;
}
