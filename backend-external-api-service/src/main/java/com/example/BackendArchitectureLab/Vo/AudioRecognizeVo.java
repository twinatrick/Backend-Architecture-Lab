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
@ApiDto(description = "語音辨識結果模型")
public class AudioRecognizeVo {
    @ApiField(description = "語音辨識轉譯文字", example = "Hello world")
    private String text;
    @ApiField(description = "音標或發音標記", example = "həˈloʊ wɜːrld")
    private String phonetic;
    @ApiField(description = "音訊持續秒數", example = "3.45")
    @JsonProperty("duration_sec")
    private Double durationSec;
    @ApiField(description = "音訊檔案存取網址", example = "https://example.com/audio/sample.wav")
    @JsonProperty("audio_url")
    private String audioUrl;
}
