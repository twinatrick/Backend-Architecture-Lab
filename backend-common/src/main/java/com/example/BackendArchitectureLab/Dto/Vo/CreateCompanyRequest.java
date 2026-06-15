package com.example.BackendArchitectureLab.Dto.Vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CreateCompanyRequest {

    @NotBlank(message = "公司名稱不得為空")
    private String name;

    @NotEmpty(message = "至少需要一個公司網址")
    private List<String> websites;

    private String description;
}
