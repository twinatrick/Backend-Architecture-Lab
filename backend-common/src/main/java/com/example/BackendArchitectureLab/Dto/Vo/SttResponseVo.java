package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SttResponseVo {
    private String text;
    private String language;
    private Double durationSec;
    private String audioUrl;
}
