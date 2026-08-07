package com.example.BackendArchitectureLab.Vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioRecognizeVo {
    private String text;
    private String phonetic;
    @JsonProperty("duration_sec")
    private Double durationSec;
    @JsonProperty("audio_url")
    private String audioUrl;
}
