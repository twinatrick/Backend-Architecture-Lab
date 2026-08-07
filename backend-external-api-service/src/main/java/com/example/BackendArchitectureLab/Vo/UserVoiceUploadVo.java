package com.example.BackendArchitectureLab.Vo;

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
public class UserVoiceUploadVo {
    private UUID id;
    private String userId;
    private String audioUrl;
    private String fileName;
    private Long fileSize;
    private Integer duration;
    private String status;
    private Date createdTime;
    private Date updatedTime;
}
