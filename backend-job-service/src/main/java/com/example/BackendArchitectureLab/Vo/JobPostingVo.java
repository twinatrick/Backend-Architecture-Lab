package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;

@Getter
@Setter
@ApiDto(description = "職缺資料傳輸物件")
public class JobPostingVo {

    @ApiField(description = "職缺唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid")
    private String id;

    @ApiField(description = "所屬公司唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid")
    private String companyId;

    @ApiField(description = "公司名稱", example = "Google")
    private String companyName;

    @ApiField(description = "職缺標題", example = "資深後端工程師")
    private String title;

    @ApiField(description = "職缺來源網址", example = "https://example.com/job/1")
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

    @ApiField(description = "Gemini AI 分析與技能標籤摘要")
    private String geminiAnalysis;

    @ApiField(description = "建立者 ID", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;

    @ApiField(description = "更新者 ID", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String updatedBy;

    @ApiField(description = "建立時間")
    private Date createdTime;

    @ApiField(description = "更新時間")
    private Date updatedTime;
}
