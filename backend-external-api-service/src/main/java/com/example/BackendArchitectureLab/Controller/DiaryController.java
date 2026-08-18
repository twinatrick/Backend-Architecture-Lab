package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Service.IVoiceDiaryService;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.VoiceDiaryVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/external/diary")
@ApiControllerTag(name = "Voice Diary", description = "語音日記查詢相關 API")
@RequiredArgsConstructor
public class DiaryController {

    private final IVoiceDiaryService voiceDiaryService;
    private final SecurityUtil securityUtil;

    @GetMapping
    @ApiOperationOk(summary = "依使用者取得語音日記", description = "取得當前登入使用者的語音日記列表。")
    public ResponseType<List<VoiceDiaryVo>> getByUser(@RequestParam(value = "userId", required = false) String userId) {
        UUID currentUserId = securityUtil.requireCurrentUserId();
        if (userId != null && !userId.isBlank() && !currentUserId.toString().equals(userId)) {
            throw new IllegalStateException("Access denied - you do not have permission to access another user's diary");
        }
        return new ResponseType<>(voiceDiaryService.findByUserId(currentUserId.toString()));
    }
}
