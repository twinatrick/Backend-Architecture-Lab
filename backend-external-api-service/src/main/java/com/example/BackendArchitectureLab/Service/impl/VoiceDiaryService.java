package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Vo.VoiceDiaryVo;
import com.example.BackendArchitectureLab.Mapper.VoiceDiaryMapper;
import com.example.BackendArchitectureLab.Repository.VoiceDiaryRepository;
import com.example.BackendArchitectureLab.Service.IVoiceDiaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VoiceDiaryService implements IVoiceDiaryService {

    @Autowired
    private VoiceDiaryRepository voiceDiaryRepository;

    @Autowired
    private VoiceDiaryMapper voiceDiaryMapper;

    @Override
    public List<VoiceDiaryVo> findByUserId(String userId) {
        return voiceDiaryRepository.findByUserIdOrderByCreatedTimeDesc(userId).stream()
                .map(voiceDiaryMapper::toVo)
                .toList();
    }
}
