package com.example.BackendArchitectureLab.Dto.Vo.Search;

import com.example.BackendArchitectureLab.Dto.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "職缺搜尋查詢參數")
public class JobPostingSearchQuery extends BaseSearchQuery {

    @Schema(description = "職缺標題（模糊查詢）")
    private String title;

    @Schema(description = "公司ID（精確查詢）")
    private String companyId;

    @Schema(description = "公司名稱（模糊查詢）")
    private String companyName;

    @Schema(description = "薪資範圍（模糊查詢）")
    private String salaryRange;

    @Schema(description = "發布日期（起始）")
    private LocalDate postedDateStart;

    @Schema(description = "發布日期（結束）")
    private LocalDate postedDateEnd;

    @Schema(description = "創建者（精確查詢）")
    private String createdBy;
}
