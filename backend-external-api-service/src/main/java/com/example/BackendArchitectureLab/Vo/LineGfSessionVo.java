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
public class LineGfSessionVo extends BaseGfSessionVo {
    private String userId;
    private Boolean pendingPrompt;
    private String gfAvatarUrl;
}
