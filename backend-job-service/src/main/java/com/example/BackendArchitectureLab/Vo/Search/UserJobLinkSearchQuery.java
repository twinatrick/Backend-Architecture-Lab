package com.example.BackendArchitectureLab.Vo.Search;

import com.example.BackendArchitectureLab.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "使用者職缺連結搜尋查詢參數")
public class UserJobLinkSearchQuery extends BaseSearchQuery {

    @Schema(description = "使用者信箱（模糊查詢）")
    private String userEmail;

    @Schema(description = "職缺職稱（模糊查詢）")
    private String jobTitle;

    @Schema(description = "公司名稱（模糊查詢）")
    private String companyName;

    @Schema(description = "使用者 UUID（精確查詢）")
    private String userId;

    @Schema(description = "職缺 UUID（精確查詢）")
    private String jobPostingId;

    @Schema(description = "創建者（精確查詢）")
    private String createdBy;
}
