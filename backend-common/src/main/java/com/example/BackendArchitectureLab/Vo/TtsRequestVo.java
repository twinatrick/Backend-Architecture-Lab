package com.example.BackendArchitectureLab.Vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsRequestVo {
    private String text;
    private String language = "zh";
    private String voiceSampleKey;
    private String voiceSampleText;
    private String voiceSampleLang;
}
