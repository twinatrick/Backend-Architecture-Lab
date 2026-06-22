package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.TtsResponseVo;

public interface ITtsService {
    TtsResponseVo synthesize(String text, String language);
}
