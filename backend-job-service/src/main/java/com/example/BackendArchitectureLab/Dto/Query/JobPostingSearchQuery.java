package com.example.BackendArchitectureLab.Dto.Query;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.Dto.Query.BaseSearchQuery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ApiDto(description = "職缺搜尋查詢參數")
public class JobPostingSearchQuery extends BaseSearchQuery {

    @ApiField(description = "職缺標題（模糊查詢）", example = "資深架構師")
    private String title;

    @ApiField(description = "公司ID（精確查詢）", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private String companyId;

    @ApiField(description = "公司名稱（模糊查詢）", example = "科技公司")
    private String companyName;

    @ApiField(description = "薪資範圍（模糊查詢）", example = "80k-120k")
    private String salaryRange;

    @ApiField(description = "發布日期（起始）", example = "2026-01-01")
    private LocalDate postedDateStart;

    @ApiField(description = "發布日期（結束）", example = "2026-12-31")
    private LocalDate postedDateEnd;

    @ApiField(description = "創建者 UUID（精確查詢）", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;
}
