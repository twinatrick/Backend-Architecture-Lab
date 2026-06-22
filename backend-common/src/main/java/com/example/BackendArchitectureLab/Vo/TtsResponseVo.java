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
public class TtsResponseVo {
    @JsonProperty("audio_url")
    private String audioUrl;
}
