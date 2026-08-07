package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.Search.VoiceUploadSearchQuery;
import com.example.BackendArchitectureLab.Vo.UserVoiceUploadVo;
import com.example.BackendArchitectureLab.Vo.VoiceTranslationVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;

import java.util.List;
import java.util.UUID;

public interface IUserVoiceUploadService {

    UserVoiceUploadVo saveUpload(UserVoiceUploadVo vo);

    UserVoiceUploadVo getUploadById(UUID id);

    PageResult<UserVoiceUploadVo> searchUserUploads(String userId, VoiceUploadSearchQuery query);

    VoiceTranslationVo saveTranslation(VoiceTranslationVo vo);

    List<VoiceTranslationVo> getTranslationsByUploadId(UUID uploadId);

    CacheListWrapper<VoiceTranslationVo> getTranslationsByUploadIdCache(UUID uploadId);
}
