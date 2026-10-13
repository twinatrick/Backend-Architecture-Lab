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
@ApiDto(description = "文字轉語音合成回應物件")
public class TtsResponseVo {
    @JsonProperty("audio_url")
    @ApiField(description = "合成語音檔案下載網址", example = "https://example.com/audio/123.wav")
    private String audioUrl;
}
