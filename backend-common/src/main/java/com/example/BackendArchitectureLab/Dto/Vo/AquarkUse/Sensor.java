package com.example.BackendArchitectureLab.Dto.Vo.AquarkUse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Sensor {
    @JsonProperty("Volt")
    private voltArray Volt;
    @JsonProperty("StickTxRh")
    private StickTxRh StickTxRh;
    @JsonProperty("Ultrasonic_Level")
    private Ultrasonic_Level Ultrasonic_Level;
    @JsonProperty("Water_speed_aquark")
    private WaterSpeedAquark Water_speed_aquark;

}
