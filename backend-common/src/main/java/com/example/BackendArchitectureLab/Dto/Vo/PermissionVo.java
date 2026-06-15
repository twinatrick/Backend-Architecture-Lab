package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter@NoArgsConstructor
public class PermissionVo {
    private String user;
    private List<String> userList;
    private String function;
    private List<String> functionList;
    private String role;
    private List<String> roleList;
}
