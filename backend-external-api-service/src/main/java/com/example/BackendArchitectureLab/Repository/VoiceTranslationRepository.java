package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.VoiceTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VoiceTranslationRepository extends JpaRepository<VoiceTranslation, UUID> {
    List<VoiceTranslation> findByVoiceUploadIdOrderByCreatedTimeDesc(UUID voiceUploadId);
}
