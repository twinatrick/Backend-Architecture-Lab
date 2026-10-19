package com.example.BackendArchitectureLab.Dto.Query;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.Dto.Query.BaseSearchQuery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ApiDto(description = "使用者職缺連結搜尋查詢參數")
public class UserJobLinkSearchQuery extends BaseSearchQuery {

    @ApiField(description = "使用者信箱（模糊查詢）", example = "user@example.com")
    private String userEmail;

    @ApiField(description = "職缺職稱（模糊查詢）", example = "後端工程師")
    private String jobTitle;

    @ApiField(description = "公司名稱（模糊查詢）", example = "科技公司")
    private String companyName;

    @ApiField(description = "使用者 UUID（精確查詢）", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private String userId;

    @ApiField(description = "職缺 UUID（精確查詢）", example = "550e8400-e29b-41d4-a716-446655440001", format = "uuid")
    private String jobPostingId;

    @ApiField(description = "創建者 UUID（精確查詢）", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;
}
