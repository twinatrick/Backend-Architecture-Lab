package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class UserJobLinkVo {
    private String id;
    private String userId;
    private String userEmail;
    private String jobPostingId;
    private String jobTitle;
    private String companyName;
    private String userNotes;
    private String geminiFeedback;
    private String createdBy;
    private String updatedBy;
    private Date createdTime;
    private Date updatedTime;
}
