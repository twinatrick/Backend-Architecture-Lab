package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@ApiDto(description = "使用者職缺連結資料傳輸物件")
public class UserJobLinkVo {

    @ApiField(description = "連結唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid")
    private String id;

    @ApiField(description = "使用者唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid")
    private String userId;

    @ApiField(description = "使用者電子信箱", example = "user@example.com")
    private String userEmail;

    @ApiField(description = "職缺唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid")
    private String jobPostingId;

    @ApiField(description = "職缺標題", example = "資深後端工程師")
    private String jobTitle;

    @ApiField(description = "公司名稱", example = "Google")
    private String companyName;

    @ApiField(description = "使用者備註筆記")
    private String userNotes;

    @ApiField(description = "Gemini AI 媒合回饋與建議")
    private String geminiFeedback;

    @ApiField(description = "建立者 ID", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;

    @ApiField(description = "更新者 ID", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String updatedBy;

    @ApiField(description = "建立時間")
    private Date createdTime;

    @ApiField(description = "更新時間")
    private Date updatedTime;
}
