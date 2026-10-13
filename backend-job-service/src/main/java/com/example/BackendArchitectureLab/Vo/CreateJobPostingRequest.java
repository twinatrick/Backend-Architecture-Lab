package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "建立職缺請求物件")
public class CreateJobPostingRequest {

    @NotBlank(message = "公司 ID 不得為空")
    @ApiField(description = "所屬公司唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid", required = true)
    private String companyId;

    @NotBlank(message = "職缺標題不得為空")
    @ApiField(description = "職缺標題", example = "資深後端工程師", required = true)
    private String title;

    @NotBlank(message = "職缺網址不得為空")
    @ApiField(description = "職缺來源網址", example = "https://example.com/job/1", required = true)
    private String url;

    @ApiField(description = "職缺詳細說明")
    private String description;

    @ApiField(description = "職位條件與要求")
    private String requirements;

    @ApiField(description = "職位工作職責")
    private String responsibilities;

    @ApiField(description = "薪資範圍", example = "80k - 100k")
    private String salaryRange;

    @ApiField(description = "發布日期")
    private LocalDate postedDate;
}
