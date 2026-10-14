package com.example.BackendArchitectureLab.Vo.AquarkUse;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ApiDto(description = "超音波水位感測數值")
public class UltrasonicLevel {
    @ApiField(description = "回波數值", example = "12.5")
    private float echo;
}
