package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.ChatResponseVo;

import java.util.List;
import java.util.Map;

public interface IChatService {
    ChatResponseVo chat(List<Map<String, String>> messages, Double temperature);
}
