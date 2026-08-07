package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Service.IUserVoiceUploadService;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.Search.VoiceUploadSearchQuery;
import com.example.BackendArchitectureLab.Vo.UserVoiceUploadVo;
import com.example.BackendArchitectureLab.Vo.VoiceTranslationVo;
import org.springframework.beans.factory.annotation.Autowired;
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
public class VoiceUploadController {

    @Autowired
    private IUserVoiceUploadService voiceUploadService;

    @Autowired
    private SecurityUtil securityUtil;

    @PostMapping
    @RequirePermission("Edit")
    public ResponseType<UserVoiceUploadVo> createUpload(@Valid @RequestBody UserVoiceUploadVo vo) {
        String currentUserId = securityUtil.requireCurrentUserId().toString();
        vo.setUserId(currentUserId);
        UserVoiceUploadVo saved = voiceUploadService.saveUpload(vo);
        return new ResponseType<>(saved);
    }

    @GetMapping("/{id}")
    @RequirePermission("View")
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
    public ResponseType<PageResult<UserVoiceUploadVo>> searchCurrentUserUploads(
            @Valid @RequestBody VoiceUploadSearchQuery query) {
        String currentUserId = securityUtil.requireCurrentUserId().toString();
        PageResult<UserVoiceUploadVo> result = voiceUploadService.searchUserUploads(currentUserId, query);
        return new ResponseType<>(result);
    }

    @PostMapping("/{id}/translations")
    @RequirePermission("Edit")
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
