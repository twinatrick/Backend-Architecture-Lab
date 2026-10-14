package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiDto(description = "語音轉文字辨識回應物件")
public class SttResponseVo {
    @ApiField(description = "辨識出的文字內容", example = "語音辨識測試內容")
    private String text;

    @ApiField(description = "偵測辨識語言", example = "zh")
    private String language;

    @JsonProperty("duration_sec")
    @ApiField(description = "音訊長度 (秒)", example = "3.5")
    private Double durationSec;

    @JsonProperty("audio_url")
    @ApiField(description = "來源音訊檔案網址", example = "https://example.com/audio/voice.wav")
    private String audioUrl;
}
