package com.example.BackendArchitectureLab.Dto.Request;

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
@ApiDto(description = "文字轉語音合成請求物件")
public class TtsRequest {
    @ApiField(description = "欲合成之文字內容", example = "你好，歡迎使用本系統", required = true)
    private String text;

    @ApiField(description = "語音語言代碼", example = "zh")
    @Builder.Default
    private String language = "zh";

    @ApiField(description = "音色範本鍵值", example = "voice-sample-01")
    private String voiceSampleKey;

    @ApiField(description = "音色範本參考文本", example = "這是一段參考音訊文本")
    private String voiceSampleText;

    @ApiField(description = "音色範本語言代碼", example = "zh")
    private String voiceSampleLang;
}
