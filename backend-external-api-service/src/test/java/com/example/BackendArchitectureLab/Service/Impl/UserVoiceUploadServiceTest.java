package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IUserVoiceUploadDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IVoiceTranslationDataAccess;
import com.example.BackendArchitectureLab.Entity.UserVoiceUpload;
import com.example.BackendArchitectureLab.Entity.VoiceTranslation;
import com.example.BackendArchitectureLab.Mapper.UserVoiceUploadMapper;
import com.example.BackendArchitectureLab.Mapper.VoiceTranslationMapper;
import com.example.BackendArchitectureLab.Service.IUserVoiceUploadService;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.Search.VoiceUploadSearchQuery;
import com.example.BackendArchitectureLab.Vo.UserVoiceUploadVo;
import com.example.BackendArchitectureLab.Vo.VoiceTranslationVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserVoiceUploadServiceTest {

    @Mock
    private IUserVoiceUploadDataAccess userVoiceUploadDataAccess;

    @Mock
    private IVoiceTranslationDataAccess voiceTranslationDataAccess;

    @Mock
    private UserVoiceUploadMapper uploadMapper;

    @Mock
    private VoiceTranslationMapper translationMapper;

    @Mock
    private TransactionExecutor transactionExecutor;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache mockCache;

    @Mock
    private IUserVoiceUploadService self;

    @InjectMocks
    private UserVoiceUploadService uploadService;

    @BeforeEach
    void setUp() {
        // Mock TransactionExecutor
        when(transactionExecutor.executeReadOnly(any())).thenAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(0);
            return supplier.get();
        });

        // Mock CacheManager
        when(cacheManager.getCache(anyString())).thenReturn(mockCache);

        // Mock Mapper toVo & toEntity
        when(uploadMapper.toVo(any(UserVoiceUpload.class))).thenAnswer(inv -> {
            UserVoiceUpload entity = inv.getArgument(0);
            if (entity == null) return null;
            return UserVoiceUploadVo.builder()
                    .id(entity.getId())
                    .userId(entity.getUserId())
                    .audioUrl(entity.getAudioUrl())
                    .fileName(entity.getFileName())
                    .fileSize(entity.getFileSize())
                    .duration(entity.getDuration())
                    .status(entity.getStatus())
                    .build();
        });

        when(uploadMapper.toEntity(any(UserVoiceUploadVo.class))).thenAnswer(inv -> {
            UserVoiceUploadVo vo = inv.getArgument(0);
            if (vo == null) return null;
            UserVoiceUpload entity = new UserVoiceUpload();
            entity.setId(vo.getId());
            entity.setUserId(vo.getUserId());
            entity.setAudioUrl(vo.getAudioUrl());
            entity.setFileName(vo.getFileName());
            entity.setFileSize(vo.getFileSize());
            entity.setDuration(vo.getDuration());
            entity.setStatus(vo.getStatus());
            return entity;
        });

        when(translationMapper.toVo(any(VoiceTranslation.class))).thenAnswer(inv -> {
            VoiceTranslation entity = inv.getArgument(0);
            if (entity == null) return null;
            VoiceTranslationVo vo = new VoiceTranslationVo();
            vo.setId(entity.getId());
            vo.setTargetLanguage(entity.getTargetLanguage());
            vo.setTranslatedText(entity.getTranslatedText());
            vo.setStatus(entity.getStatus());
            vo.setTranslationEngine(entity.getTranslationEngine());
            if (entity.getVoiceUpload() != null) {
                vo.setVoiceUploadId(entity.getVoiceUpload().getId());
            }
            return vo;
        });

        when(translationMapper.toEntity(any(VoiceTranslationVo.class))).thenAnswer(inv -> {
            VoiceTranslationVo vo = inv.getArgument(0);
            if (vo == null) return null;
            VoiceTranslation entity = new VoiceTranslation();
            entity.setId(vo.getId());
            entity.setTargetLanguage(vo.getTargetLanguage());
            entity.setTranslatedText(vo.getTranslatedText());
            entity.setStatus(vo.getStatus());
            entity.setTranslationEngine(vo.getTranslationEngine());
            return entity;
        });
    }

    @Test
    @DisplayName("saveUpload 應能成功保存語音上傳並清理搜尋快取")
    void shouldSaveUploadSuccessfully() {
        UserVoiceUploadVo vo = UserVoiceUploadVo.builder()
                .userId("user-123")
                .audioUrl("http://audio.url")
                .fileName("test.wav")
                .build();

        UserVoiceUpload savedEntity = new UserVoiceUpload();
        savedEntity.setId(UUID.randomUUID());
        savedEntity.setUserId("user-123");
        savedEntity.setAudioUrl("http://audio.url");
        savedEntity.setFileName("test.wav");

        when(userVoiceUploadDataAccess.save(any(UserVoiceUpload.class))).thenReturn(savedEntity);

        UserVoiceUploadVo result = uploadService.saveUpload(vo);

        assertNotNull(result);
        assertEquals(savedEntity.getId(), result.getId());
        assertEquals("user-123", result.getUserId());

        verify(userVoiceUploadDataAccess, times(1)).save(any(UserVoiceUpload.class));
        verify(stringRedisTemplate, times(1)).keys("userVoiceUploads::search:user-123:*");
    }

    @Test
    @DisplayName("getUploadById 應能正確回傳對應的語音上傳")
    void shouldGetUploadById() {
        UUID uploadId = UUID.randomUUID();
        UserVoiceUpload entity = new UserVoiceUpload();
        entity.setId(uploadId);
        entity.setUserId("user-456");
        entity.setAudioUrl("http://audio.url/2");

        when(userVoiceUploadDataAccess.findById(uploadId)).thenReturn(Optional.of(entity));

        UserVoiceUploadVo result = uploadService.getUploadById(uploadId);

        assertNotNull(result);
        assertEquals(uploadId, result.getId());
        assertEquals("user-456", result.getUserId());
    }

    @Test
    @DisplayName("searchUserUploads 應能進行動態分頁搜尋")
    void shouldSearchUserUploads() {
        String userId = "user-789";
        VoiceUploadSearchQuery query = new VoiceUploadSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("createdTime");
        query.setSortDir("desc");

        UserVoiceUpload entity = new UserVoiceUpload();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setFileName("record.wav");

        Page<UserVoiceUpload> page = new PageImpl<>(List.of(entity));
        when(userVoiceUploadDataAccess.searchByUserId(eq(userId), any(VoiceUploadSearchQuery.class), any(Pageable.class)))
                .thenReturn(page);

        PageResult<UserVoiceUploadVo> result = uploadService.searchUserUploads(userId, query);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("record.wav", result.getContent().get(0).getFileName());
    }

    @Test
    @DisplayName("saveTranslation 應能成功保存翻譯紀錄並清理快取")
    void shouldSaveTranslationSuccessfully() {
        UUID uploadId = UUID.randomUUID();
        UserVoiceUpload upload = new UserVoiceUpload();
        upload.setId(uploadId);

        VoiceTranslationVo vo = new VoiceTranslationVo();
        vo.setVoiceUploadId(uploadId);
        vo.setTargetLanguage("en");
        vo.setTranslatedText("Hello");

        VoiceTranslation savedTranslation = new VoiceTranslation();
        savedTranslation.setId(UUID.randomUUID());
        savedTranslation.setVoiceUpload(upload);
        savedTranslation.setTargetLanguage("en");
        savedTranslation.setTranslatedText("Hello");

        when(userVoiceUploadDataAccess.findById(uploadId)).thenReturn(Optional.of(upload));
        when(voiceTranslationDataAccess.save(any(VoiceTranslation.class))).thenReturn(savedTranslation);

        VoiceTranslationVo result = uploadService.saveTranslation(vo);

        assertNotNull(result);
        assertEquals(savedTranslation.getId(), result.getId());
        assertEquals(uploadId, result.getVoiceUploadId());

        verify(voiceTranslationDataAccess, times(1)).save(any(VoiceTranslation.class));
        verify(mockCache, times(1)).evict("byupload:" + uploadId);
    }

    @Test
    @DisplayName("saveTranslation 當快取為 null 時應能容錯正常儲存")
    void shouldSaveTranslation_whenCacheIsNull_shouldNotThrow() {
        UUID uploadId = UUID.randomUUID();
        UserVoiceUpload upload = new UserVoiceUpload();
        upload.setId(uploadId);

        VoiceTranslationVo vo = new VoiceTranslationVo();
        vo.setVoiceUploadId(uploadId);
        vo.setTargetLanguage("en");
        vo.setTranslatedText("Hello");

        VoiceTranslation savedTranslation = new VoiceTranslation();
        savedTranslation.setId(UUID.randomUUID());
        savedTranslation.setVoiceUpload(upload);

        when(userVoiceUploadDataAccess.findById(uploadId)).thenReturn(Optional.of(upload));
        when(voiceTranslationDataAccess.save(any(VoiceTranslation.class))).thenReturn(savedTranslation);
        when(cacheManager.getCache("voiceTranslations")).thenReturn(null);

        VoiceTranslationVo result = uploadService.saveTranslation(vo);

        assertNotNull(result);
        verify(voiceTranslationDataAccess, times(1)).save(any(VoiceTranslation.class));
    }

    @Test
    @DisplayName("getTranslationsByUploadIdCache 應能正確回傳快取列表")
    void shouldGetTranslationsByUploadIdCache() {
        UUID uploadId = UUID.randomUUID();
        UserVoiceUpload upload = new UserVoiceUpload();
        upload.setId(uploadId);

        VoiceTranslation entity = new VoiceTranslation();
        entity.setId(UUID.randomUUID());
        entity.setVoiceUpload(upload);
        entity.setTargetLanguage("ja");
        entity.setTranslatedText("こんにちは");

        when(voiceTranslationDataAccess.findByVoiceUploadIdOrderByCreatedTimeDesc(uploadId))
                .thenReturn(List.of(entity));

        CacheListWrapper<VoiceTranslationVo> result = uploadService.getTranslationsByUploadIdCache(uploadId);

        assertNotNull(result);
        assertEquals(1, result.getData().size());
        assertEquals("ja", result.getData().get(0).getTargetLanguage());
    }
}
