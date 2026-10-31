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
@ApiDto(description = "語音日記記錄模型")
public class VoiceDiaryVo {
    @ApiField(description = "語音日記唯一識別碼", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private UUID id;
    @ApiField(description = "所屬使用者識別碼", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String userId;
    @ApiField(description = "語音轉譯之日記文字內容", example = "今天完成了微服務架構無環依賴重構。")
    private String content;
    @ApiField(description = "語音檔案存取網址", example = "https://example.com/audio/diary.wav")
    private String audioUrl;
    @ApiField(description = "日記建立時間", example = "2026-04-12T08:00:00Z")
    private Date createdTime;
    @ApiField(description = "最後更新時間", example = "2026-04-12T08:05:00Z")
    private Date updatedTime;
}
