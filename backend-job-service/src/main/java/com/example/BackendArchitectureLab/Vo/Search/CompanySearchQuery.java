package com.example.BackendArchitectureLab.Vo.Search;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.Vo.Common.BaseSearchQuery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ApiDto(description = "公司搜尋查詢參數")
public class CompanySearchQuery extends BaseSearchQuery {

    @ApiField(description = "關鍵字（模糊搜尋公司名稱與描述）", example = "科技")
    private String keyword;

    @ApiField(description = "公司名稱（模糊查詢）", example = "未來科技")
    private String name;

    @ApiField(description = "公司描述（模糊查詢）", example = "軟體研發")
    private String description;

    @ApiField(description = "創建者 UUID（精確查詢）", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;
}
