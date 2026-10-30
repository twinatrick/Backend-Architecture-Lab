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
@ApiDto(description = "使用者語音上傳記錄模型")
public class UserVoiceUploadVo {
    @ApiField(description = "語音上傳記錄唯一識別碼", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private UUID id;
    @ApiField(description = "上傳者使用者識別碼", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String userId;
    @ApiField(description = "音訊檔案存取網址", example = "https://example.com/audio/upload.wav")
    private String audioUrl;
    @ApiField(description = "原始音訊檔案名稱", example = "recording.wav")
    private String fileName;
    @ApiField(description = "音訊檔案大小（Bytes）", example = "204800")
    private Long fileSize;
    @ApiField(description = "音訊長度（秒）", example = "15")
    private Integer duration;
    @ApiField(description = "處理狀態", example = "COMPLETED")
    private String status;
    @ApiField(description = "上傳建立時間", example = "2026-04-12T08:00:00Z")
    private Date createdTime;
    @ApiField(description = "最後更新時間", example = "2026-04-12T08:01:00Z")
    private Date updatedTime;
}
