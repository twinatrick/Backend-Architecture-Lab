package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotConfigVo {
    private UUID id;
    private String platform;
    private String configKey;
    private String configValue;
    private String description;
    private Date createdTime;
    private Date updatedTime;
}
