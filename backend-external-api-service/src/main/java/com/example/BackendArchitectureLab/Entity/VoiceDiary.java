package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "voice_diary")
public class VoiceDiary extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "audio_url")
    private String audioUrl;

    @Column(name = "transcript")
    private String transcript;

    @Column(name = "language")
    private String language;

    @Column(name = "source")
    private String source;
}
