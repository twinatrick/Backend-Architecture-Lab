package com.example.BackendArchitectureLab.Util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 統一管理搜尋排序策略（允許的排序欄位與方向驗證）。
 * <p>
 * Default Sort（sortBy=createdTime, sortDir=desc）由 {@link com.example.BackendArchitectureLab.Vo.Common.PageQuery} 內建，
 * 此處僅負責欄位白名單與方向驗證，避免各 Service 重複宣告 String[] allowedSortFields。
 */
public class SearchSortPolicy {

    private final Set<String> allowedSortFields;

    public SearchSortPolicy(String... allowedSortFields) {
        this.allowedSortFields = Arrays.stream(allowedSortFields)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void validate(String sortBy, String sortDir) {
        SortFieldValidator.validate(sortBy, sortDir, allowedSortFields);
    }
}
