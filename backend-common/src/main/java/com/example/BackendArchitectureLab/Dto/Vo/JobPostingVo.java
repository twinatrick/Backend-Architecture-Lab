package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;

@Getter
@Setter
public class JobPostingVo {
    private String id;
    private String companyId;
    private String companyName;
    private String title;
    private String url;
    private String description;
    private String requirements;
    private String responsibilities;
    private String salaryRange;
    private LocalDate postedDate;
    private String geminiAnalysis;
    private String createdBy;
    private String updatedBy;
    private Date createdTime;
    private Date updatedTime;
}
