package com.example.BackendArchitectureLab.Dto.Query;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用分頁查詢參數
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiDto(description = "分頁查詢參數")
public class PageQuery {
    
    @ApiField(description = "頁碼，從0開始", example = "0")
    @Min(value = 0, message = "頁碼必須大於等於0")
    private Integer page = 0;
    
    @ApiField(description = "每頁大小", example = "20")
    @Min(value = 1, message = "每頁大小必須大於0")
    @Max(value = 100, message = "每頁大小不能超過100")
    private Integer size = 20;
    
    @ApiField(description = "排序欄位", example = "createdTime")
    private String sortBy = "createdTime";
    
    @ApiField(description = "排序方向：asc/desc", example = "desc")
    private String sortDir = "desc";
    
    /**
     * 驗證排序方向是否合法
     */
    public boolean isValidSortDir() {
        return "asc".equalsIgnoreCase(sortDir) || "desc".equalsIgnoreCase(sortDir);
    }
    
    /**
     * 獲取標準化的排序方向（轉為小寫）
     */
    public String getNormalizedSortDir() {
        return sortDir == null ? "desc" : sortDir.toLowerCase();
    }
}
