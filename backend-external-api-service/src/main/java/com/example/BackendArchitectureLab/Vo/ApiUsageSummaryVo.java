package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiDto(description = "API 使用統計摘要模型")
public class ApiUsageSummaryVo {

    @ApiField(description = "總呼叫次數", example = "120")
    private long totalCalls;

    @ApiField(description = "預估總費用", example = "1.2500")
    private BigDecimal totalCost;

    @ApiField(description = "總輸入量（Token 或字元數）", example = "45000")
    private long totalInput;

    @ApiField(description = "統計起始時間", example = "2026-04-01T00:00:00Z")
    private Date start;

    @ApiField(description = "統計結束時間", example = "2026-04-30T23:59:59Z")
    private Date end;

    @ApiField(description = "篩選之目標服務名稱", example = "OPENAI")
    private String service;
}
