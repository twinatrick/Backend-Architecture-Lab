package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Vo.AudioRecognizeVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Service.ILearnService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/stt")
@ApiControllerTag(name = "Speech To Text Provider", description = "指定 STT Provider 的語音辨識端點")
@RequiredArgsConstructor
public class SttController {

    private final ILearnService learnService;

    @Value("${stt.providers.whisper.enabled:true}")
    private boolean whisperEnabled;

    @Value("${stt.providers.sensevoice.enabled:false}")
    private boolean sensevoiceEnabled;

    @PostMapping(value = "/whisper", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperationBadRequest(summary = "以 Whisper 引擎語音辨識", description = "固定以 Whisper 進行語音辨識與拼音轉換。")
    public ResponseType<AudioRecognizeVo> recognizeWithWhisper(
            @Parameter(description = "音訊檔案", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "目標語言，如 zh (繁體中文) 或 ja (日文)") @RequestParam(value = "language", defaultValue = "zh") String language,
            @Parameter(description = "輸出模式：pinyin, zhuyin, romaji, none") @RequestParam(value = "mode", defaultValue = "none") String mode) {
        if (!whisperEnabled) {
            return ResponseType.Fail("NOT_AVAILABLE", "Whisper provider 未啟用", 404);
        }
        if (file.isEmpty()) {
            return ResponseType.Fail("BAD_REQUEST", "請上傳音訊檔案", 400);
        }

        AudioRecognizeVo result = learnService.processAudio(file, language, mode, "whisper");
        return ResponseType.Success(result, "音訊辨識成功");
    }

    @PostMapping(value = "/sensevoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperationBadRequest(summary = "以 SenseVoice 引擎語音辨識", description = "固定以 SenseVoice 進行語音辨識與拼音轉換。")
    public ResponseType<AudioRecognizeVo> recognizeWithSenseVoice(
            @Parameter(description = "音訊檔案", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "目標語言，如 zh (繁體中文) 或 ja (日文)") @RequestParam(value = "language", defaultValue = "zh") String language,
            @Parameter(description = "輸出模式：pinyin, zhuyin, romaji, none") @RequestParam(value = "mode", defaultValue = "none") String mode) {
        if (!sensevoiceEnabled) {
            return ResponseType.Fail("NOT_AVAILABLE", "SenseVoice provider 未啟用", 404);
        }
        if (file.isEmpty()) {
            return ResponseType.Fail("BAD_REQUEST", "請上傳音訊檔案", 400);
        }

        AudioRecognizeVo result = learnService.processAudio(file, language, mode, "sensevoice");
        return ResponseType.Success(result, "音訊辨識成功");
    }
}
