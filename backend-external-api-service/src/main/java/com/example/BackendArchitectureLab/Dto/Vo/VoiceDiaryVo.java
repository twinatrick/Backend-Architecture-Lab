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
public class VoiceDiaryVo {
    private UUID id;
    private String userId;
    private String content;
    private String audioUrl;
    private Date createdTime;
    private Date updatedTime;
}
