package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.DataAccess.IUserVoiceUploadDataAccess;
import com.example.BackendArchitectureLab.DataAccess.specification.VoiceUploadSpecification;
import com.example.BackendArchitectureLab.Entity.UserVoiceUpload;
import com.example.BackendArchitectureLab.Repository.UserVoiceUploadRepository;
import com.example.BackendArchitectureLab.Vo.Search.VoiceUploadSearchQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * IUserVoiceUploadDataAccess 實作。
 * 委派 UserVoiceUploadRepository 執行資料存取。
 */
@Component
public class UserVoiceUploadDataAccessImpl implements IUserVoiceUploadDataAccess {

    @Autowired
    private UserVoiceUploadRepository userVoiceUploadRepository;

    @Override
    public UserVoiceUpload save(UserVoiceUpload entity) {
        return userVoiceUploadRepository.save(entity);
    }

    @Override
    public Optional<UserVoiceUpload> findById(UUID id) {
        return userVoiceUploadRepository.findById(id);
    }

    @Override
    public Page<UserVoiceUpload> searchByUserId(String userId, VoiceUploadSearchQuery query, Pageable pageable) {
        return userVoiceUploadRepository.findAll(
                VoiceUploadSpecification.buildSpecification(userId, query),
                pageable
        );
    }
}
