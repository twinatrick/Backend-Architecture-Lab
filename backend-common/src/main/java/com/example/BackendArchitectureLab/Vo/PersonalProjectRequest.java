package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 個人專案請求 Vo
 * 用於一般使用者新增或修改個人專案
 */
@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "個人專案新增與異動請求物件")
public class PersonalProjectRequest {
    @ApiField(description = "專案名稱", example = "微服務重構專案", required = true)
    private String name;

    @ApiField(description = "專案描述", example = "將單體架構拆分為分散式微服務")
    private String description;
}
