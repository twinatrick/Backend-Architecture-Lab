package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Vo.SttResponseVo;
import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Service.ISttService;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SttService implements ISttService {

    @Autowired
    private AiPyServiceFeignClient aiPyServiceFeignClient;

    @Autowired
    private IUsageTrackService usageTrackService;

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.stt-bucket}")
    private String bucket;

    private void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            System.err.println("自動建立 MinIO 儲存桶 [" + bucket + "] 失敗: " + e.getMessage());
        }
    }

    @Override
    public SttResponseVo recognize(byte[] fileData, String language) {
        return recognize(fileData, language, null);
    }

    @Override
    public SttResponseVo recognize(byte[] fileData, String language, String provider) {
        if (fileData == null || fileData.length == 0) {
            throw new IllegalArgumentException("fileData is empty");
        }

        String objectKey = "stt-temp/" + UUID.randomUUID().toString() + ".wav";
        try {
            // 確保儲存桶存在 (自動建立機制)
            ensureBucketExists();

            // 上傳至 MinIO 暫存區
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(fileData), fileData.length, -1)
                    .contentType("audio/wav")
                    .build());

            // 呼叫 Feign（可依 provider 指定轉譯引擎）
            SttResponseVo vo = aiPyServiceFeignClient.recognize(objectKey, language, provider);

            // 以 1 小時時效 presigned URL 回填 audioUrl（python 拼湊的 audio_url 不使用）
            if (vo != null) {
                String presignedUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(objectKey)
                        .expiry(1, TimeUnit.HOURS)
                        .build());
                vo.setAudioUrl(presignedUrl);
            }
            return vo;
        } catch (Exception e) {
            throw new RuntimeException("STT 暫存中轉處理異常: " + e.getMessage(), e);
        } finally {
            // 保證清除 MinIO 暫存檔
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build());
            } catch (Exception e) {
                System.err.println("無法清除 MinIO 中的 STT 暫存檔: " + e.getMessage());
            }
        }
    }

    @Override
    public String recognizeAndTrack(byte[] fileData, String language, String trackServiceKey) {
        if (fileData == null || fileData.length == 0) {
            return "";
        }
        String lang = (language == null || language.isBlank()) ? "zh" : language;
        try {
            SttResponseVo stt = recognize(fileData, lang);
            String text = (stt != null && stt.getText() != null) ? stt.getText().trim() : "";
            if (!text.isEmpty()) {
                if (trackServiceKey != null && !trackServiceKey.isBlank()) {
                    usageTrackService.track(trackServiceKey, "stt", "file", 1L);
                }
            }
            return text;
        } catch (Exception e) {
            throw new RuntimeException("語音辨識服務異常: " + e.getMessage(), e);
        }
    }
}
