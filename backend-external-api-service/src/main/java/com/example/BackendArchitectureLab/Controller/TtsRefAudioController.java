package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.Ignore;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiQuery;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Service.ITtsRefAudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class TtsRefAudioController {

    private final ITtsRefAudioService ttsRefAudioService;

    @Ignore
    @PostMapping(value = "/external/tts/ref-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseType<String> uploadRefAudio(
            @ApiQuery(name = "channel", required = true, description = "目標頻道或角色識別碼", example = "gf-default") String channel,
            @ApiQuery(name = "file", required = true, description = "上傳之參考音訊檔案") MultipartFile file,
            @ApiQuery(name = "text", required = true, description = "參考音訊對應之文字稿", example = "こんにちは") String text,
            @ApiQuery(name = "lang", defaultValue = "ja", description = "參考音訊語言代碼", example = "ja") String lang) throws Exception {
        String url = ttsRefAudioService.upload(channel, file, text, lang);
        return new ResponseType<>(url);
    }
}
