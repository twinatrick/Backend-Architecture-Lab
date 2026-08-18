package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.TtsRequestVo;
import com.example.BackendArchitectureLab.Vo.TtsResponseVo;
import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Service.ITtsService;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TtsService implements ITtsService {

    @Value("${minio.bucket:audio}")
    private String bucket;

    private final AiPyServiceFeignClient aiPyServiceFeignClient;
    private final IUsageTrackService usageTrackService;
    private final MinioClient minioClient;

    @Override
    public TtsResponseVo synthesize(String text, String language) {
        TtsRequestVo request = TtsRequestVo.builder()
                .text(text)
                .language(language)
                .build();
        TtsResponseVo response = aiPyServiceFeignClient.synthesize(request);
        usageTrackService.track("tts", "synthesize", "char", (long) text.length());
        return response;
    }

    @Override
    public byte[] downloadAudio(String audioUrl) throws Exception {
        if (audioUrl == null || audioUrl.isBlank()) {
            throw new IllegalArgumentException("audioUrl is null or empty");
        }

        try {
            URI uri = new URI(audioUrl);
            String path = uri.getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            int firstSlash = path.indexOf("/");
            if (firstSlash != -1) {
                String bucketName = path.substring(0, firstSlash);
                String objectKey = path.substring(firstSlash + 1);

                try (InputStream is = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectKey)
                                .build())) {
                    return is.readAllBytes();
                }
            }
        } catch (Exception e) {
            // 解析或 MinIO 直接下載失敗，回退至原來的 HTTP GET 下載
        }

        URL url = new URL(audioUrl);
        try (InputStream is = url.openStream()) {
            return is.readAllBytes();
        }
    }

    @Override
    public String getPresignedUrl(String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank()) {
            return audioUrl;
        }

        try {
            URI uri = new URI(audioUrl);
            String path = uri.getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            int firstSlash = path.indexOf("/");
            if (firstSlash != -1) {
                String bucketName = path.substring(0, firstSlash);
                String objectKey = path.substring(firstSlash + 1);

                return minioClient.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.GET)
                                .bucket(bucketName)
                                .object(objectKey)
                                .expiry(15, TimeUnit.MINUTES)
                                .build()
                );
            }
        } catch (Exception e) {
            // 發生異常時，直接傳回原始的 audioUrl
        }
        return audioUrl;
    }

    /**
     * 動作過濾器：過濾括弧內部的心理描寫與動作描述，只保留純台詞進行語音合成
     */
    public static String filterActionsForTts(String text) {
        if (text == null) {
            return "";
        }
        // 移除中文全型括號（...）、英文半型括號 (...) 還有星號 *...* 內部的所有動作描述
        String filtered = text.replaceAll("（[^）]*）", "")
                              .replaceAll("\\([^)]*\\)", "")
                              .replaceAll("\\*[^\\*]*\\*", "");
        return filtered.trim();
    }

    @Override
    public byte[] downloadTtsFile(String fileName) throws Exception {
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object("tts/" + fileName)
                        .build())) {
            return is.readAllBytes();
        }
    }
}
