package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Entity.VoiceTranslation;
import com.example.BackendArchitectureLab.Repository.VoiceTranslationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceTranslationDataAccessImplTest {

    @Mock
    private VoiceTranslationRepository voiceTranslationRepository;

    private VoiceTranslationDataAccessImpl dataAccess;

    @BeforeEach
    void setUp() {
        dataAccess = new VoiceTranslationDataAccessImpl();
        ReflectionTestUtils.setField(dataAccess, "voiceTranslationRepository", voiceTranslationRepository);
    }

    @Test
    void save_DelegatesToRepository() {
        VoiceTranslation entity = new VoiceTranslation();
        when(voiceTranslationRepository.save(entity)).thenReturn(entity);
        assertEquals(entity, dataAccess.save(entity));
    }

    @Test
    void findByVoiceUploadId_DelegatesToRepository() {
        UUID voiceUploadId = UUID.randomUUID();
        List<VoiceTranslation> list = List.of(new VoiceTranslation());
        when(voiceTranslationRepository.findByVoiceUploadIdOrderByCreatedTimeDesc(voiceUploadId)).thenReturn(list);
        assertEquals(list, dataAccess.findByVoiceUploadIdOrderByCreatedTimeDesc(voiceUploadId));
    }
}