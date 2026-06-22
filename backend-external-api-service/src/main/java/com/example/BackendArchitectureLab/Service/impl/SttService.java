package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Vo.SttResponseVo;
import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Service.ISttService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SttService implements ISttService {

    @Autowired
    private AiPyServiceFeignClient aiPyServiceFeignClient;

    @Override
    public SttResponseVo recognize(byte[] fileData, String language) {
        return aiPyServiceFeignClient.recognize(fileData, language);
    }
}
