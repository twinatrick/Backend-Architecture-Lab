package com.example.BackendArchitectureLab.Vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscordGfMessageVo {
    private UUID id;
    private UUID sessionId;
    private String role;
    private String content;
    private String senderName;
    private Date createdTime;
    private Date updatedTime;
}
