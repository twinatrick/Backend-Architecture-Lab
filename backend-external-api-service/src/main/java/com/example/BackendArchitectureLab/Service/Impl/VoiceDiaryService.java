package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.VoiceDiaryVo;
import com.example.BackendArchitectureLab.Mapper.VoiceDiaryMapper;
import com.example.BackendArchitectureLab.Repository.VoiceDiaryRepository;
import com.example.BackendArchitectureLab.Service.IVoiceDiaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VoiceDiaryService implements IVoiceDiaryService {

    private final VoiceDiaryRepository voiceDiaryRepository;
    private final VoiceDiaryMapper voiceDiaryMapper;

    @Override
    public List<VoiceDiaryVo> findByUserId(String userId) {
        return voiceDiaryRepository.findByUserIdOrderByCreatedTimeDesc(userId).stream()
                .map(voiceDiaryMapper::toVo)
                .toList();
    }
}
