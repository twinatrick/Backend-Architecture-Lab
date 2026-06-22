package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Vo.ChatRequestVo;
import com.example.BackendArchitectureLab.Vo.ChatResponseVo;
import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Service.IChatService;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ChatService implements IChatService {

    @Autowired
    private AiPyServiceFeignClient aiPyServiceFeignClient;

    @Autowired
    private IUsageTrackService usageTrackService;

    @Override
    public ChatResponseVo chat(List<Map<String, String>> messages, Double temperature) {
        ChatRequestVo request = ChatRequestVo.builder()
                .messages(messages)
                .temperature(temperature)
                .stream(false)
                .build();
        ChatResponseVo response = aiPyServiceFeignClient.chat(request);
        usageTrackService.track("chat", "chat", "message", (long) messages.size());
        return response;
    }
}
