package com.example.BackendArchitectureLab.Feign;

import com.example.BackendArchitectureLab.Dto.Vo.ChatRequestVo;
import com.example.BackendArchitectureLab.Dto.Vo.ChatResponseVo;
import com.example.BackendArchitectureLab.Dto.Vo.SttResponseVo;
import com.example.BackendArchitectureLab.Dto.Vo.TtsRequestVo;
import com.example.BackendArchitectureLab.Dto.Vo.TtsResponseVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;

@FeignClient(name = "ai-py-service")
public interface AiPyServiceFeignClient {

    @PostMapping(value = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    SttResponseVo recognize(@RequestPart("file") byte[] fileData,
                          @RequestParam("language") String language);

    @PostMapping("/tts")
    TtsResponseVo synthesize(@RequestBody TtsRequestVo request);

    @PostMapping("/chat")
    ChatResponseVo chat(@RequestBody ChatRequestVo request);
}
