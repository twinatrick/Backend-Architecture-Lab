package com.example.BackendArchitectureLab.Vo.Search;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.Vo.Common.BaseSearchQuery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * User 搜尋查詢參數
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ApiDto(description = "使用者搜尋查詢參數")
public class UserSearchQuery extends BaseSearchQuery {
    
    @ApiField(description = "使用者名稱（模糊查詢）", example = "張三")
    private String name;
    
    @ApiField(description = "電子郵件（模糊查詢）", example = "user@example.com")
    private String email;
    
    @ApiField(description = "電話號碼（模糊查詢）", example = "0912345678")
    private String phone;
    
    @ApiField(description = "是否停用", example = "false")
    private Boolean disabled;
    
    @ApiField(description = "創建者 UUID（精確查詢）", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;
}
