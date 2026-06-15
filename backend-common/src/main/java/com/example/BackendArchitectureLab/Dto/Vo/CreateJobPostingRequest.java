package com.example.BackendArchitectureLab.Dto.Vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class CreateJobPostingRequest {

    @NotBlank(message = "公司 ID 不得為空")
    private String companyId;

    @NotBlank(message = "職缺標題不得為空")
    private String title;

    @NotBlank(message = "職缺網址不得為空")
    private String url;

    private String description;
    private String requirements;
    private String responsibilities;
    private String salaryRange;
    private LocalDate postedDate;
}
