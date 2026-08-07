package com.example.BackendArchitectureLab.Feign;

import com.example.BackendArchitectureLab.Vo.ChatRequestVo;
import com.example.BackendArchitectureLab.Vo.ChatResponseVo;
import com.example.BackendArchitectureLab.Vo.SttResponseVo;
import com.example.BackendArchitectureLab.Vo.TtsRequestVo;
import com.example.BackendArchitectureLab.Vo.TtsResponseVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;

@FeignClient(name = "ai-py-service")
public interface AiPyServiceFeignClient {

    @PostMapping("/stt")
    SttResponseVo recognize(@RequestParam("object_key") String objectKey,
                          @RequestParam("language") String language,
                          @RequestParam(value = "provider", required = false) String provider);

    @PostMapping("/stt/whisper")
    SttResponseVo recognizeWithWhisper(@RequestParam("object_key") String objectKey,
                                     @RequestParam("language") String language);

    @PostMapping("/stt/sensevoice")
    SttResponseVo recognizeWithSenseVoice(@RequestParam("object_key") String objectKey,
                                        @RequestParam("language") String language);

    @PostMapping("/tts")
    TtsResponseVo synthesize(@RequestBody TtsRequestVo request);

    @PostMapping("/chat")
    ChatResponseVo chat(@RequestBody ChatRequestVo request);
}
