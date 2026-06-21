package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.TtsRequestVo;
import com.example.BackendArchitectureLab.Dto.Vo.TtsResponseVo;
import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Service.ITtsService;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TtsService implements ITtsService {

    @Autowired
    private AiPyServiceFeignClient aiPyServiceFeignClient;

    @Autowired
    private IUsageTrackService usageTrackService;

    @Override
    public TtsResponseVo synthesize(String text, String language) {
        TtsRequestVo request = TtsRequestVo.builder()
                .text(text)
                .language(language)
                .build();
        TtsResponseVo response = aiPyServiceFeignClient.synthesize(request);
        usageTrackService.track("tts", "synthesize", "char", (long) text.length());
        return response;
    }
}
