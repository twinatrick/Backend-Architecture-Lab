package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 個人專案請求 DTO
 * 用於一般使用者新增或修改個人專案
 */
@Getter
@Setter
@NoArgsConstructor
public class PersonalProjectRequest {
    /**
     * 專案名稱
     */
    private String name;
    
    /**
     * 專案描述
     */
    private String description;
}
