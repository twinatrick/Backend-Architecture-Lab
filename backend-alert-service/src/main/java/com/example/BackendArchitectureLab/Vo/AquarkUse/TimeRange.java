package com.example.BackendArchitectureLab.Vo.AquarkUse;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@ApiDto(description = "時間區間查詢條件")
public class TimeRange {

    @ApiField(description = "開始時間", example = "2026-10-01T00:00:00.000Z")
    private Date start;

    @ApiField(description = "結束時間", example = "2026-10-31T23:59:59.000Z")
    private Date end;
}
