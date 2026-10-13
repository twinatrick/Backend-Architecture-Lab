package com.example.BackendArchitectureLab.Vo.AquarkUse;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ApiDto(description = "各通道電壓數值陣列")
public class VoltArray {
    @ApiField(description = "通道 1 電壓", example = "3.3")
    private float v1;
    @ApiField(description = "通道 2 電壓", example = "3.3")
    private float v2;
    @ApiField(description = "通道 3 電壓", example = "3.3")
    private float v3;
    @ApiField(description = "通道 4 電壓", example = "3.3")
    private float v4;
    @ApiField(description = "通道 5 電壓", example = "3.3")
    private float v5;
    @ApiField(description = "通道 6 電壓", example = "3.3")
    private float v6;
    @ApiField(description = "通道 7 電壓", example = "3.3")
    private float v7;
}
