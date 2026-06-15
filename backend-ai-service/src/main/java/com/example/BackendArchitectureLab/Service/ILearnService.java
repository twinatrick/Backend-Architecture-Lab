package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Vo.AudioRecognizeVo;
import org.springframework.web.multipart.MultipartFile;

public interface ILearnService {
    AudioRecognizeVo processAudio(MultipartFile file, String lang, String mode);
}
