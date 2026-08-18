package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Service.IUserVoiceUploadService;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.Search.VoiceUploadSearchQuery;
import com.example.BackendArchitectureLab.Vo.UserVoiceUploadVo;
import com.example.BackendArchitectureLab.Vo.VoiceTranslationVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/voice-uploads")
@ApiControllerTag(name = "Voice Uploads", description = "語音上傳與翻譯管理相關 API")
@RequiredArgsConstructor
public class VoiceUploadController {

    private final IUserVoiceUploadService voiceUploadService;
    private final SecurityUtil securityUtil;

    @PostMapping
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "建立語音上傳", description = "建立新的語音上傳記錄並綁定當前使用者。")
    public ResponseType<UserVoiceUploadVo> createUpload(@Valid @RequestBody UserVoiceUploadVo vo) {
        String currentUserId = securityUtil.requireCurrentUserId().toString();
        vo.setUserId(currentUserId);
        UserVoiceUploadVo saved = voiceUploadService.saveUpload(vo);
        return new ResponseType<>(saved);
    }

    @GetMapping("/{id}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "依 ID 取得語音上傳", description = "依 ID 取得語音上傳記錄，僅限擁有者。")
    public ResponseType<UserVoiceUploadVo> getUpload(@PathVariable("id") UUID id) {
        UserVoiceUploadVo upload = voiceUploadService.getUploadById(id);
        String currentUserId = securityUtil.requireCurrentUserId().toString();
        
        // BOLA 安全防護驗證
        if (!currentUserId.equals(upload.getUserId())) {
            throw new IllegalStateException("Access denied - you do not own this voice upload");
        }
        
        return new ResponseType<>(upload);
    }

    @PostMapping("/current/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "搜尋當前使用者上傳記錄", description = "分頁搜尋當前使用者的語音上傳記錄。")
    public ResponseType<PageResult<UserVoiceUploadVo>> searchCurrentUserUploads(
            @Valid @RequestBody VoiceUploadSearchQuery query) {
        String currentUserId = securityUtil.requireCurrentUserId().toString();
        PageResult<UserVoiceUploadVo> result = voiceUploadService.searchUserUploads(currentUserId, query);
        return new ResponseType<>(result);
    }

    @PostMapping("/{id}/translations")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "建立語音翻譯", description = "為指定語音上傳建立翻譯記錄，僅限擁有者。")
    public ResponseType<VoiceTranslationVo> createTranslation(
            @PathVariable("id") UUID uploadId,
            @Valid @RequestBody VoiceTranslationVo vo) {
        UserVoiceUploadVo upload = voiceUploadService.getUploadById(uploadId);
        String currentUserId = securityUtil.requireCurrentUserId().toString();

        // BOLA 安全防護驗證
        if (!currentUserId.equals(upload.getUserId())) {
            throw new IllegalStateException("Access denied - you do not own this voice upload");
        }

        vo.setVoiceUploadId(uploadId);
        VoiceTranslationVo saved = voiceUploadService.saveTranslation(vo);
        return new ResponseType<>(saved);
    }

    @GetMapping("/{id}/translations")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得語音翻譯列表", description = "取得指定語音上傳的所有翻譯記錄，僅限擁有者。")
    public ResponseType<List<VoiceTranslationVo>> getTranslations(@PathVariable("id") UUID uploadId) {
        UserVoiceUploadVo upload = voiceUploadService.getUploadById(uploadId);
        String currentUserId = securityUtil.requireCurrentUserId().toString();

        // BOLA 安全防護驗證
        if (!currentUserId.equals(upload.getUserId())) {
            throw new IllegalStateException("Access denied - you do not own this voice upload");
        }

        List<VoiceTranslationVo> list = voiceUploadService.getTranslationsByUploadId(uploadId);
        return new ResponseType<>(list);
    }
}
