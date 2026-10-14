package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@ApiDto(description = "公司資料傳輸物件")
public class CompanyVo {

    @ApiField(description = "公司唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid")
    private String id;

    @ApiField(description = "公司名稱", example = "Google")
    private String name;

    @ApiField(description = "公司官方網站清單")
    private List<String> websites;

    @ApiField(description = "公司簡介與說明", example = "跨國科技公司")
    private String description;

    @ApiField(description = "建立者 ID", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;

    @ApiField(description = "更新者 ID", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String updatedBy;

    @ApiField(description = "建立時間")
    private Date createdTime;

    @ApiField(description = "更新時間")
    private Date updatedTime;
}
