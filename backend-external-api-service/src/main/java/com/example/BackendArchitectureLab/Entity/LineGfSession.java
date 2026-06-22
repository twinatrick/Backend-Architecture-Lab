package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "line_gf_session")
public class LineGfSession extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "active")
    private Boolean active = false;

    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "voice_enabled")
    private Boolean voiceEnabled = false;

    @Column(name = "voice_sample_key")
    private String voiceSampleKey;

    @Column(name = "voice_sample_text", columnDefinition = "TEXT")
    private String voiceSampleText;

    @Column(name = "pending_prompt")
    private Boolean pendingPrompt = false;

    @Column(name = "gf_name")
    private String gfName;

    @Column(name = "gf_avatar_url", columnDefinition = "TEXT")
    private String gfAvatarUrl;

    @Column(name = "conversation_history", columnDefinition = "TEXT")
    private String conversationHistory;
}
