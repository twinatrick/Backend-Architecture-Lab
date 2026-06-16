package com.example.BackendArchitectureLab.Dto.Vo.Search;

import com.example.BackendArchitectureLab.Dto.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "公司搜尋查詢參數")
public class CompanySearchQuery extends BaseSearchQuery {

    @Schema(description = "關鍵字（模糊搜尋公司名稱與描述）")
    private String keyword;

    @Schema(description = "公司名稱（模糊查詢）")
    private String name;

    @Schema(description = "公司描述（模糊查詢）")
    private String description;

    @Schema(description = "創建者（精確查詢）")
    private String createdBy;
}
