package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.VoiceDiaryVo;
import com.example.BackendArchitectureLab.Service.IVoiceDiaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/external/diary")
@ApiControllerTag(name = "Voice Diary", description = "語音日記查詢相關 API")
@RequiredArgsConstructor
public class DiaryController {

    private final IVoiceDiaryService voiceDiaryService;

    @GetMapping
    @ApiOperationOk(summary = "依使用者取得語音日記", description = "依使用者 ID 取得語音日記列表。")
    public ResponseType<List<VoiceDiaryVo>> getByUser(@RequestParam("userId") String userId) {
        return new ResponseType<>(voiceDiaryService.findByUserId(userId));
    }
}
