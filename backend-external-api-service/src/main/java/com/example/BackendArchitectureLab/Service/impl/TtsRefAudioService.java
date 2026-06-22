package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Service.ITtsRefAudioService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Service
public class TtsRefAudioService implements ITtsRefAudioService {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Override
    public String upload(String channel, MultipartFile file, String text, String lang) throws Exception {
        String baseKey = "tts-refs/" + channel;
        String wavKey = baseKey + "/current.wav";

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(wavKey)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());

        String configJson = "{\"text\":\"" + escapeJson(text) + "\",\"lang\":\"" + escapeJson(lang) + "\"}";
        byte[] configBytes = configJson.getBytes(StandardCharsets.UTF_8);
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(baseKey + "/current.json")
                .stream(new ByteArrayInputStream(configBytes), configBytes.length, -1)
                .contentType("application/json")
                .build());

        return "http://localhost:9000/" + bucket + "/" + wavKey;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
