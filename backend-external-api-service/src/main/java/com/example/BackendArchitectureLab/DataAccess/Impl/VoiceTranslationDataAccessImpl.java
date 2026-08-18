package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IVoiceTranslationDataAccess;
import com.example.BackendArchitectureLab.Entity.VoiceTranslation;
import com.example.BackendArchitectureLab.Repository.VoiceTranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * IVoiceTranslationDataAccess 實作。
 * 委派 VoiceTranslationRepository 執行資料存取。
 */
@Component
@RequiredArgsConstructor
public class VoiceTranslationDataAccessImpl implements IVoiceTranslationDataAccess {

    private final VoiceTranslationRepository voiceTranslationRepository;

    @Override
    public VoiceTranslation save(VoiceTranslation entity) {
        return voiceTranslationRepository.save(entity);
    }

    @Override
    public List<VoiceTranslation> findByVoiceUploadIdOrderByCreatedTimeDesc(UUID voiceUploadId) {
        return voiceTranslationRepository.findByVoiceUploadIdOrderByCreatedTimeDesc(voiceUploadId);
    }
}
