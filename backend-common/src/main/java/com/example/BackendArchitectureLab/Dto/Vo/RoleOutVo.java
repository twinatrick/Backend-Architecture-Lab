package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.UUID;
@Getter
@Setter
@NoArgsConstructor
public class RoleOutVo {
    private UUID id;
    private String name;
    private String description;
    private String createdBy;
    private String updatedBy;
    private Date createdTime;
    private Date updatedTime;
    private List<String> functionIds;
}
