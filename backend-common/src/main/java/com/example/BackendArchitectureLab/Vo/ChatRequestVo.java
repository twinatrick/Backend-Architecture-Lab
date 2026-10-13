package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiDto(description = "AI 對話請求物件")
public class ChatRequestVo {
    @ApiField(description = "歷史對話訊息清單")
    private List<Map<String, String>> messages;

    @ApiField(description = "生成溫度參數", example = "0.7")
    private Double temperature;

    @ApiField(description = "是否啟用串流輸出", example = "false")
    @Builder.Default
    private Boolean stream = false;
}
