package com.example.BackendArchitectureLab.Vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiJobPostingVo {
    private String title;
    private String url;
    private String description;
    private String requirements;
    private String responsibilities;
    private String salaryRange;
}
