package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.SttResponseVo;

public interface ISttService {
    SttResponseVo recognize(byte[] fileData, String language);
    String recognizeAndTrack(byte[] fileData, String language, String trackServiceKey);
}
