package com.example.BackendArchitectureLab.Dto.Vo.Common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 通用分頁結果
 * @param <T> 資料類型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "分頁結果")
public class PageResult<T> {
    
    @Schema(description = "資料列表")
    private List<T> content;
    
    @Schema(description = "總記錄數")
    private Long totalElements;
    
    @Schema(description = "總頁數")
    private Integer totalPages;
    
    @Schema(description = "當前頁碼（從0開始）")
    private Integer currentPage;
    
    @Schema(description = "每頁大小")
    private Integer pageSize;
    
    @Schema(description = "是否有下一頁")
    private Boolean hasNext;
    
    @Schema(description = "是否有上一頁")
    private Boolean hasPrevious;
    
    @Schema(description = "是否為第一頁")
    private Boolean isFirst;
    
    @Schema(description = "是否為最後一頁")
    private Boolean isLast;
    
    /**
     * 從 Spring Data Page 物件建立 PageResult
     */
    public static <T> PageResult<T> of(Page<T> page) {
        return PageResult.<T>builder()
                .content(page.getContent())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .build();
    }
    
    /**
     * 從 Spring Data Page 物件建立 PageResult，並轉換資料類型
     * @param page 原始 Page 物件
     * @param content 轉換後的資料列表
     */
    public static <T> PageResult<T> of(Page<?> page, List<T> content) {
        return PageResult.<T>builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .build();
    }
}
