package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IUserVoiceUploadDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IVoiceTranslationDataAccess;
import com.example.BackendArchitectureLab.Entity.UserVoiceUpload;
import com.example.BackendArchitectureLab.Entity.VoiceTranslation;
import com.example.BackendArchitectureLab.Mapper.UserVoiceUploadMapper;
import com.example.BackendArchitectureLab.Mapper.VoiceTranslationMapper;
import com.example.BackendArchitectureLab.Service.IUserVoiceUploadService;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.Search.VoiceUploadSearchQuery;
import com.example.BackendArchitectureLab.Vo.UserVoiceUploadVo;
import com.example.BackendArchitectureLab.Vo.VoiceTranslationVo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserVoiceUploadService implements IUserVoiceUploadService {

    private static final SearchSortPolicy SEARCH_SORT_POLICY = new SearchSortPolicy(
            "id", "fileName", "fileSize", "duration", "status", "createdTime", "updatedTime"
    );

    private final IUserVoiceUploadDataAccess userVoiceUploadDataAccess;
    private final IVoiceTranslationDataAccess voiceTranslationDataAccess;
    private final UserVoiceUploadMapper uploadMapper;
    private final VoiceTranslationMapper translationMapper;
    private final TransactionExecutor transactionExecutor;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheManager cacheManager;
    @Lazy
    private final IUserVoiceUploadService self;

    @Override
    @Transactional
    @CachePut(value = "userVoiceUploads", key = "#result.id")
    public UserVoiceUploadVo saveUpload(UserVoiceUploadVo vo) {
        UserVoiceUpload entity = uploadMapper.toEntity(vo);
        UserVoiceUpload saved = userVoiceUploadDataAccess.save(entity);
        UserVoiceUploadVo resultVo = uploadMapper.toVo(saved);

        // 1. 精確模糊清除此使用者的分頁搜尋快取
        evictUserVoiceUploadsSearchCache(vo.getUserId());

        return resultVo;
    }

    @Override
    @Cacheable(value = "userVoiceUploads", key = "#id", sync = true)
    public UserVoiceUploadVo getUploadById(UUID id) {
        return transactionExecutor.executeReadOnly(() -> {
            UserVoiceUpload upload = userVoiceUploadDataAccess.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Voice upload not found"));
            return uploadMapper.toVo(upload);
        });
    }

    @Override
    @Cacheable(value = "userVoiceUploads", key = "'search:' + #userId + ':' + #query.toString()", sync = true)
    public PageResult<UserVoiceUploadVo> searchUserUploads(String userId, VoiceUploadSearchQuery query) {
        return transactionExecutor.executeReadOnly(() -> {
            // 驗證排序欄位與方向
            SEARCH_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());

            // 排序與分頁建構
            Sort sort = Sort.by("asc".equalsIgnoreCase(query.getNormalizedSortDir()) ? Sort.Direction.ASC : Sort.Direction.DESC, query.getSortBy());
            Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), sort);

            // 動態 JPA Specification 查詢（包含強制的 userId 隔離）
            Page<UserVoiceUpload> page = userVoiceUploadDataAccess.searchByUserId(userId, query, pageable);

            List<UserVoiceUploadVo> vos = page.getContent().stream()
                    .map(uploadMapper::toVo)
                    .toList();

            return PageResult.of(page, vos);
        });
    }

    @Override
    @Transactional
    @CachePut(value = "voiceTranslations", key = "#result.id")
    public VoiceTranslationVo saveTranslation(VoiceTranslationVo vo) {
        UserVoiceUpload upload = userVoiceUploadDataAccess.findById(vo.getVoiceUploadId())
                .orElseThrow(() -> new IllegalArgumentException("Voice upload not found"));

        VoiceTranslation entity = translationMapper.toEntity(vo);
        entity.setVoiceUpload(upload);

        VoiceTranslation saved = voiceTranslationDataAccess.save(entity);
        VoiceTranslationVo resultVo = translationMapper.toVo(saved);

        // 2. 清除該語音對應的翻譯清單快取
        try {
            var cache = cacheManager.getCache("voiceTranslations");
            if (cache != null) {
                cache.evict("byupload:" + vo.getVoiceUploadId());
            }
        } catch (Exception e) {
            // 快取清除容錯，不干擾核心業務
        }

        return resultVo;
    }

    @Override
    public List<VoiceTranslationVo> getTranslationsByUploadId(UUID uploadId) {
        // 透過 self 代理調用，確保內部調用 AOP 快取依然 100% 生效
        return self.getTranslationsByUploadIdCache(uploadId).getData();
    }

    @Override
    @Cacheable(value = "voiceTranslations", key = "'byupload:' + #uploadId", sync = true)
    public CacheListWrapper<VoiceTranslationVo> getTranslationsByUploadIdCache(UUID uploadId) {
        return transactionExecutor.executeReadOnly(() -> {
            List<VoiceTranslationVo> list = voiceTranslationDataAccess.findByVoiceUploadIdOrderByCreatedTimeDesc(uploadId).stream()
                    .map(translationMapper::toVo)
                    .toList();
            return new CacheListWrapper<>(list);
        });
    }

    private void evictUserVoiceUploadsSearchCache(String userId) {
        try {
            Set<String> keys = stringRedisTemplate.keys("userVoiceUploads::search:" + userId + ":*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            // 快取清除容錯，不干擾核心業務
        }
    }
}
