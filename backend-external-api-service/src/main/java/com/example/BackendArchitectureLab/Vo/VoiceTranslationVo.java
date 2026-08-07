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
public class VoiceTranslationVo {
    private UUID id;
    private UUID voiceUploadId;
    private String targetLanguage;
    private String translatedText;
    private String status;
    private String translationEngine;
    private Date createdTime;
    private Date updatedTime;
}
