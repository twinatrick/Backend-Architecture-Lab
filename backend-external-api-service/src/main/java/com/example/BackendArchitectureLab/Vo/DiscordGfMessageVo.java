package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ApiDto(description = "Discord 對話訊息模型")
public class DiscordGfMessageVo {
    @ApiField(description = "訊息唯一識別碼", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private UUID id;
    @ApiField(description = "所屬對話識別碼", example = "00000000-0000-0000-0000-000000000002", format = "uuid")
    private UUID sessionId;
    @ApiField(description = "訊息角色", example = "user")
    private String role;
    @ApiField(description = "訊息內容", example = "今天過得如何？")
    private String content;
    @ApiField(description = "發送者顯示名稱", example = "小明")
    private String senderName;
    @ApiField(description = "訊息建立時間", example = "2026-04-12T08:00:00Z")
    private Date createdTime;
    @ApiField(description = "訊息最後更新時間", example = "2026-04-12T08:01:00Z")
    private Date updatedTime;
}
