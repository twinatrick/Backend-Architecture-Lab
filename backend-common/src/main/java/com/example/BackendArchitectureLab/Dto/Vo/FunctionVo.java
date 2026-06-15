package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FunctionVo {
    private String id ;
    private String name="";
    private String parent="";
    private String sort="";
    private Integer type;
    private String parentName="";
    private String grandParentId="";
    private boolean disabled;
    private boolean edit;
    private boolean newAdd;
    private String newName="";
    private boolean delete;

}
