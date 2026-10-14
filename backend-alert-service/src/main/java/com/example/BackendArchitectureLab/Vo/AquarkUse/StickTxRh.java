package com.example.BackendArchitectureLab.Vo.AquarkUse;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ApiDto(description = "溫濕度感測數值")
public class StickTxRh {
    @ApiField(description = "濕度數值 (RH)", example = "65.2")
    private float rh;
    @ApiField(description = "溫度數值 (TX)", example = "26.8")
    private float tx;
}
