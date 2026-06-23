package com.example.BackendArchitectureLab.Vo;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DiscordGfSessionVo extends BaseGfSessionVo {
    private String guildId;
    private String channelId;
    private String userId;
    private String gfAvatarUrl;
}
