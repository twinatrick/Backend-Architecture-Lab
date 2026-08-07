package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.UserVoiceUpload;
import com.example.BackendArchitectureLab.Vo.Search.VoiceUploadSearchQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * UserVoiceUpload 資料存取介面。
 * 抽象 UserVoiceUploadRepository 供 Service 層使用。
 */
public interface IUserVoiceUploadDataAccess {

    UserVoiceUpload save(UserVoiceUpload entity);

    Optional<UserVoiceUpload> findById(UUID id);

    /**
     * 依使用者與查詢條件分頁查詢（強制 userId 隔離）。
     */
    Page<UserVoiceUpload> searchByUserId(String userId, VoiceUploadSearchQuery query, Pageable pageable);
}
