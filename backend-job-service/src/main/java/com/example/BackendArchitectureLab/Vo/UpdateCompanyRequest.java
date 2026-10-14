package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "更新公司請求物件")
public class UpdateCompanyRequest {

    @ApiField(description = "公司唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid", required = true)
    private UUID id;

    @ApiField(description = "公司名稱", example = "Google", required = true)
    private String name;

    @ApiField(description = "公司官方網站清單")
    private List<String> websites;

    @ApiField(description = "公司簡介與說明", example = "跨國科技公司")
    private String description;
}
