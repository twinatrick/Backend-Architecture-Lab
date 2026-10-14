package com.example.BackendArchitectureLab.Vo.AquarkUse;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ApiDto(description = "水流速度感測數值")
public class WaterSpeedAquark {
    @ApiField(description = "水流速度", example = "1.25")
    private float speed;
}
