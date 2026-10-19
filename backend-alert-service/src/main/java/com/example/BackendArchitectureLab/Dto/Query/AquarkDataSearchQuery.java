package com.example.BackendArchitectureLab.Dto.Query;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.DataAccess.specification.CriteriaApiFilter;
import com.example.BackendArchitectureLab.Dto.Query.BaseSearchQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiDto(description = "Aquark 水情資料分頁搜尋參數")
public class AquarkDataSearchQuery extends BaseSearchQuery {

    @ApiField(description = "進階動態過濾條件清單（選填）")
    private List<CriteriaApiFilter> filters = new ArrayList<>();

    public AquarkDataSearchQuery() {
        setSortBy("trans_time");
        setSortDir("desc");
        setSize(20);
        setPage(0);
    }
}
