package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Dto.Vo.AudioRecognizeVo;
import com.example.BackendArchitectureLab.Dto.Vo.ResponseType;
import com.example.BackendArchitectureLab.Service.ILearnService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/stt/v1")
@ApiControllerTag(name = "Speech To Text", description = "音訊辨識與拼音轉換")
public class LearnController {

    @Autowired
    private ILearnService learnService;

    @PostMapping(value = "/{lan}/{mode}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "語音辨識與拼音轉換", description = "上傳音訊進行 Whisper 辨識，並根據語言及模式轉換為拼音、注音或羅馬音。")
    public ResponseType<AudioRecognizeVo> recognizeAudio(
            @Parameter(description = "目標語言，如 zh 或 ja", required = true) @PathVariable("lan") String lan,
            @Parameter(description = "輸出模式：pinyin, zhuyin, romaji, none", required = true) @PathVariable("mode") String mode,
            @Parameter(description = "音訊檔案", required = true) @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            return ResponseType.Fail("BAD_REQUEST", "請上傳音訊檔案", 400);
        }

        AudioRecognizeVo result = learnService.processAudio(file, lan, mode);
        return ResponseType.Success(result, "音訊辨識成功");
    }
}
