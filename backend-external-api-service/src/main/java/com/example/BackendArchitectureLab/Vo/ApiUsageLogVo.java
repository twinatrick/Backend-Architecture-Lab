package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiDto(description = "外部 API 呼叫與使用記錄模型")
public class ApiUsageLogVo {
    @ApiField(description = "使用記錄唯一識別碼", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private UUID id;
    @ApiField(description = "外部服務名稱", example = "OPENAI")
    private String service;
    @ApiField(description = "API 呼叫類型", example = "CHAT_COMPLETION")
    private String callType;
    @ApiField(description = "輸入用量（Token 數或秒數）", example = "1024")
    private Long inputAmount;
    @ApiField(description = "計價單位單價", example = "0.00015")
    private BigDecimal inputUnit;
    @ApiField(description = "預估呼叫成本", example = "0.1536")
    private BigDecimal estimatedCost;
    @ApiField(description = "記錄建立時間", example = "2026-04-12T08:00:00Z")
    private Date createdTime;
    @ApiField(description = "記錄最後更新時間", example = "2026-04-12T08:00:00Z")
    private Date updatedTime;
}
