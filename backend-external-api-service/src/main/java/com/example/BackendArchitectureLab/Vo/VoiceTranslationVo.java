package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiDto(description = "語音翻譯結果模型")
public class VoiceTranslationVo {
    @ApiField(description = "翻譯結果唯一識別碼", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private UUID id;
    @ApiField(description = "來源語音上傳識別碼", example = "00000000-0000-0000-0000-000000000002", format = "uuid")
    private UUID voiceUploadId;
    @ApiField(description = "目標語言代碼", example = "en")
    private String targetLanguage;
    @ApiField(description = "翻譯後文字", example = "How are you today?")
    private String translatedText;
    @ApiField(description = "翻譯處理狀態", example = "COMPLETED")
    private String status;
    @ApiField(description = "使用的翻譯引擎", example = "openai")
    private String translationEngine;
    @ApiField(description = "翻譯結果建立時間", example = "2026-04-12T08:00:00Z")
    private Date createdTime;
    @ApiField(description = "翻譯結果最後更新時間", example = "2026-04-12T08:01:00Z")
    private Date updatedTime;
}
