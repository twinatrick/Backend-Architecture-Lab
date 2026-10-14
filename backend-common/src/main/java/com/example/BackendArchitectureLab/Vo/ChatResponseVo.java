package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiDto(description = "AI 對話回應物件")
public class ChatResponseVo {
    @ApiField(description = "模型回覆內容", example = "你好！很高興為您服務。")
    private String content;

    @ApiField(description = "生成使用的模型代號", example = "gemini-1.5-pro")
    private String model;
}
