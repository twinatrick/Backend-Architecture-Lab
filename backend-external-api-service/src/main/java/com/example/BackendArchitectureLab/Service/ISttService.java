package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Vo.SttResponseVo;

public interface ISttService {
    SttResponseVo recognize(byte[] fileData, String language);
}
