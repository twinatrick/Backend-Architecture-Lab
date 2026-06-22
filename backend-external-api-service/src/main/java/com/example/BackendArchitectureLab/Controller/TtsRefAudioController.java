package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.Ignore;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Service.ITtsRefAudioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class TtsRefAudioController {

    @Autowired
    private ITtsRefAudioService ttsRefAudioService;

    @Ignore
    @PostMapping(value = "/external/tts/ref-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseType<String> uploadRefAudio(
            @RequestParam("channel") String channel,
            @RequestParam("file") MultipartFile file,
            @RequestParam("text") String text,
            @RequestParam(value = "lang", defaultValue = "ja") String lang) throws Exception {
        String url = ttsRefAudioService.upload(channel, file, text, lang);
        return new ResponseType<>(url);
    }
}
