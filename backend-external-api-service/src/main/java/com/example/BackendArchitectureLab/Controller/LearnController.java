package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Vo.AudioRecognizeVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Service.ILearnService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/stt/v1")
@ApiControllerTag(name = "Speech To Text", description = "音訊辨識與拼音轉換")
@RequiredArgsConstructor
public class LearnController {

    private final ILearnService learnService;

    @PostMapping(value = "/{lan}/{mode}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperationBadRequest(
            summary = "語音辨識與拼音轉換",
            description = "上傳音訊進行 Whisper 辨識，並根據語言及模式轉換為拼音、注音或羅馬音。"
                    + " [參數說明] lan: 目標語言，如 zh (繁體中文) 或 ja (日文)；"
                    + "mode: 輸出模式 (pinyin, zhuyin, romaji, none)；file: 音訊檔案。"
    )
    public ResponseType<AudioRecognizeVo> recognizeAudio(
            @PathVariable("lan") String lan,
            @PathVariable("mode") String mode,
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            return ResponseType.Fail("BAD_REQUEST", "請上傳音訊檔案", 400);
        }

        AudioRecognizeVo result = learnService.processAudio(file, lan, mode);
        return ResponseType.Success(result, "音訊辨識成功");
    }
}
