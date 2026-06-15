package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class UserVo {
    private String id;
    private String name;
    private String email;
    private String password;
    private String phone;
    private boolean disabled;
    private String createdBy;
    private String updatedBy;
    private Date createdTime;
    private Date updatedTime;


    private List<String> roleArr;

    private List<FunctionVo> permissions;
}
