package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.VoiceDiaryVo;

import java.util.List;

public interface IVoiceDiaryService {
    List<VoiceDiaryVo> findByUserId(String userId);
}
