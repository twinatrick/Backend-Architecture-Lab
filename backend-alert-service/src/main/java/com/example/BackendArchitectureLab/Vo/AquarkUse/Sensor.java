package com.example.BackendArchitectureLab.Vo.AquarkUse;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ApiDto(description = "Aquark 感測器原始集合資料")
public class Sensor {
    @JsonProperty("Volt")
    @ApiField(description = "各通道電壓數值陣列")
    private VoltArray volt;

    @JsonProperty("StickTxRh")
    @ApiField(description = "溫濕度感測數值")
    private StickTxRh stickTxRh;

    @JsonProperty("Ultrasonic_Level")
    @ApiField(description = "超音波水位感測數值")
    private UltrasonicLevel ultrasonicLevel;

    @JsonProperty("Water_speed_aquark")
    @ApiField(description = "水流速度感測數值")
    private WaterSpeedAquark waterSpeedAquark;
}
