package com.example.BackendArchitectureLab.Dto.Vo.Search;

import com.example.BackendArchitectureLab.Dto.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "使用者搜尋查詢參數")
public class UserSearchQuery extends BaseSearchQuery {
    
    @Schema(description = "使用者名稱（模糊查詢）", example = "張三")
    private String name;
    
    @Schema(description = "電子郵件（模糊查詢）", example = "user@example.com")
    private String email;
    
    @Schema(description = "電話號碼（模糊查詢）", example = "0912345678")
    private String phone;
    
    @Schema(description = "是否停用", example = "false")
    private Boolean disabled;
    
    @Schema(description = "創建者（精確查詢）", example = "admin")
    private String createdBy;
}
