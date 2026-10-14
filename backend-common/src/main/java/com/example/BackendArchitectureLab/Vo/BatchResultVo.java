package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiDto(description = "批次查詢通用回應物件")
public class BatchResultVo<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiField(description = "查詢成功之實體清單")
    private List<T> items;

    @ApiField(description = "依 ID 為鍵之實體對照表")
    private Map<String, T> itemMap;

    @ApiField(description = "資料庫中已刪除或未找到之 ID 清單")
    private List<String> missingIds;

    @ApiField(description = "請求查詢之 ID 總數", example = "4")
    private Integer totalRequested;

    @ApiField(description = "實際查得之實體總數", example = "2")
    private Integer totalFound;

    public static <T> BatchResultVo<T> empty(int requestedCount) {
        return BatchResultVo.<T>builder()
                .items(Collections.emptyList())
                .itemMap(Collections.emptyMap())
                .missingIds(Collections.emptyList())
                .totalRequested(requestedCount)
                .totalFound(0)
                .build();
    }
}
