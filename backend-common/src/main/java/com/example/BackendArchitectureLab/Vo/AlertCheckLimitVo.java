package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ApiDto(description = "告警檢測門檻資料傳輸物件")
public class AlertCheckLimitVo implements Serializable {

    @ApiField(description = "門檻規則 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private UUID id;

    @ApiField(description = "目標資料庫資料表名稱", example = "aquark_data")
    private String tableName;

    @ApiField(description = "目標監控欄位名稱", example = "temperature")
    private String columnName;

    @ApiField(description = "檢測限制閾值", example = "85.0")
    private double limitValue;
}
