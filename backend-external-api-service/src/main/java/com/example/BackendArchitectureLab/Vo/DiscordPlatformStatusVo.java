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
public class DiscordPlatformStatusVo {
    private boolean configured;
    private String gfStatus;
    private String diaryStatus;
    private Long gfPing;
    private Long diaryPing;
    private long sessionCount;
    private long subscriptionCount;
}
