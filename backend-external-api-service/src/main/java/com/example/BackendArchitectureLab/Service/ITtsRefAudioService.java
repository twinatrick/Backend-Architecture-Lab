package com.example.BackendArchitectureLab.Service;

import org.springframework.web.multipart.MultipartFile;

public interface ITtsRefAudioService {
    String upload(String channel, MultipartFile file, String text, String lang) throws Exception;
}
