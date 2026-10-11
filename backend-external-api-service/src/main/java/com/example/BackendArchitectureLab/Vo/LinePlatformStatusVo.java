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
public class LinePlatformStatusVo {
    private boolean configured;
    private boolean channelTokenConfigured;
    private boolean channelSecretConfigured;
    private long sessionCount;
}
