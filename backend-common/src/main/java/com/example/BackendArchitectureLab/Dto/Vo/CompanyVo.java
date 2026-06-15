package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class CompanyVo {
    private String id;
    private String name;
    private List<String> websites;
    private String description;
    private String createdBy;
    private String updatedBy;
    private Date createdTime;
    private Date updatedTime;
}
