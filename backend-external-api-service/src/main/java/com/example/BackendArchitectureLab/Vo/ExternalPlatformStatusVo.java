package com.example.BackendArchitectureLab.Vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalPlatformStatusVo {
    private DiscordPlatformStatusVo discord;
    private LinePlatformStatusVo line;
    private AiPlatformStatusVo ai;
}
