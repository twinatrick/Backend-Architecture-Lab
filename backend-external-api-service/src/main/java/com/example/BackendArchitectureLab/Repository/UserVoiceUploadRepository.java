package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.UserVoiceUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserVoiceUploadRepository extends JpaRepository<UserVoiceUpload, UUID>, JpaSpecificationExecutor<UserVoiceUpload> {
}
