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
@Table(name = "user_voice_upload")
public class UserVoiceUpload extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "audio_url", nullable = false)
    private String audioUrl;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "status")
    private String status;
}
