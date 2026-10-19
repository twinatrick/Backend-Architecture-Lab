package com.example.BackendArchitectureLab.Dto.Request;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "建立公司請求物件")
public class CreateCompanyRequest {

    @NotBlank(message = "公司名稱不得為空")
    @ApiField(description = "公司名稱", example = "Google", required = true)
    private String name;

    @NotEmpty(message = "至少需要一個公司網址")
    @ApiField(description = "公司官方網站清單", required = true)
    private List<String> websites;

    @ApiField(description = "公司簡介與說明", example = "跨國科技公司")
    private String description;
}
