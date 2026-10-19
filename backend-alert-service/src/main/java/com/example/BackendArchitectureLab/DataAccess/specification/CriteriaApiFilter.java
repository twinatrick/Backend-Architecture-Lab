package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@ApiDto(description = "Aquark 感測數據動態查詢過濾條件")
public class CriteriaApiFilter {

    @ApiField(description = "資料庫欄位名稱", example = "station_name")
    private String columnName;

    @ApiField(description = "資料型別 (0: 字串, 1: 數值, 2: 日期, 3: 布林)", example = "0")
    private int type = 0;

    @ApiField(description = "字串過濾值 (type=0 時使用)", example = "STATION_01")
    private String stringValue;

    @ApiField(description = "數值過濾值 (type=1 時使用)", example = "25.5")
    private double doubleValue;

    @ApiField(description = "日期過濾值 (type=2 時使用)", example = "2026-10-13T00:00:00.000Z")
    private Date date;

    @ApiField(description = "布林過濾值 (type=3 時使用)", example = "true")
    private boolean booleanValue;

    @ApiField(description = "是否大於條件 (大於 / 大於等於)", example = "true")
    private boolean greaterThan;

    @ApiField(description = "是否小於條件 (小於 / 小於等於)", example = "false")
    private boolean lessThan;

    @ApiField(description = "是否模糊匹配 (LIKE %value%)", example = "false")
    private boolean like;

    @ApiField(description = "是否包含等於條件", example = "true")
    private boolean equal;
}
