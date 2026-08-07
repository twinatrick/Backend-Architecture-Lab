package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.VoiceTranslation;

import java.util.List;
import java.util.UUID;

/**
 * VoiceTranslation 資料存取介面。
 * 抽象 VoiceTranslationRepository 供 Service 層使用。
 */
public interface IVoiceTranslationDataAccess {

    VoiceTranslation save(VoiceTranslation entity);

    List<VoiceTranslation> findByVoiceUploadIdOrderByCreatedTimeDesc(UUID voiceUploadId);
}
