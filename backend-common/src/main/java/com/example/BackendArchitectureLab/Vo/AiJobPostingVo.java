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
@ApiDto(description = "AI 職缺分析資料傳輸物件")
public class AiJobPostingVo {
    @ApiField(description = "職缺職稱", example = "資深後端工程師")
    private String title;

    @ApiField(description = "職缺來源網址", example = "https://example.com/job/123")
    private String url;

    @ApiField(description = "職缺描述簡述", example = "負責分散式微服務架構設計與開發")
    private String description;

    @ApiField(description = "應徵要求條件", example = "精通 Java 21 與 Spring Boot")
    private String requirements;

    @ApiField(description = "核心工作職責", example = "系統模組維護與效能調優")
    private String responsibilities;

    @ApiField(description = "薪資範圍", example = "100k - 150k TWD")
    private String salaryRange;
}
