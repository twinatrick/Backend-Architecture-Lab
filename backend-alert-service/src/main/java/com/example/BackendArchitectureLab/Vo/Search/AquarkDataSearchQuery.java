package com.example.BackendArchitectureLab.Vo.Search;

import com.example.BackendArchitectureLab.Vo.AquarkUse.CriteriaAPIFilter;
import com.example.BackendArchitectureLab.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Aquark 水情資料分頁搜尋參數")
public class AquarkDataSearchQuery extends BaseSearchQuery {

    @Schema(description = "進階過濾條件清單（選填）")
    private List<CriteriaAPIFilter> filters = new ArrayList<>();

    public AquarkDataSearchQuery() {
        // 水情時序資料預設以觀測時間降序排序，每頁 20 筆
        setSortBy("trans_time");
        setSortDir("desc");
        setSize(20);
        setPage(0);
    }
}
