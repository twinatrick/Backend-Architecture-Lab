package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import com.example.BackendArchitectureLab.Vo.SttResponseVo;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SttServiceTest {

    @Mock
    private AiPyServiceFeignClient aiPyServiceFeignClient;

    @Mock
    private IUsageTrackService usageTrackService;

    @Mock
    private MinioClient minioClient;

    @InjectMocks
    private SttService sttService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sttService, "bucket", "user-audio");
    }

    @Test
    @DisplayName("recognize 成功時應以 1 小時 presigned URL 回填 audioUrl 並回傳結果")
    void recognizeShouldFillPresignedAudioUrl() throws Exception {
        // Arrange
        SttResponseVo feignResponse = new SttResponseVo();
        feignResponse.setText("你好世界");
        feignResponse.setLanguage("zh");
        feignResponse.setDurationSec(2.5);

        when(aiPyServiceFeignClient.recognize(any(), any(), any())).thenReturn(feignResponse);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/user-audio/presigned-url");

        // Act
        SttResponseVo vo = sttService.recognize(new byte[]{1, 2, 3}, "zh");

        // Assert
        assertNotNull(vo);
        assertEquals("你好世界", vo.getText());
        assertEquals(2.5, vo.getDurationSec());
        assertEquals("http://localhost:9000/user-audio/presigned-url", vo.getAudioUrl());
        verify(minioClient, times(1)).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    @DisplayName("recognize 成功後應於 finally 清除 MinIO 暫存檔")
    void recognizeShouldCleanupTempObjectInFinally() throws Exception {
        // Arrange
        SttResponseVo feignResponse = new SttResponseVo();
        feignResponse.setText("測試");
        feignResponse.setLanguage("zh");

        when(aiPyServiceFeignClient.recognize(any(), any(), any())).thenReturn(feignResponse);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/user-audio/presigned-url");

        // Act
        sttService.recognize(new byte[]{1, 2, 3}, "zh");

        // Assert
        verify(minioClient, times(1)).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("Feign 回傳 null 時不應嘗試產生 presigned URL")
    void recognizeShouldSkipPresignedWhenResponseNull() throws Exception {
        // Arrange
        when(aiPyServiceFeignClient.recognize(any(), any(), any())).thenReturn(null);

        // Act
        SttResponseVo vo = sttService.recognize(new byte[]{1, 2, 3}, "zh");

        // Assert
        assertNull(vo);
        verify(minioClient, never()).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
        verify(minioClient, times(1)).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("fileData 為空時應拋出 IllegalArgumentException")
    void recognizeShouldRejectEmptyFileData() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> sttService.recognize(new byte[0], "zh"));
        assertThrows(IllegalArgumentException.class, () -> sttService.recognize(null, "zh"));
    }

    @Test
    @DisplayName("recognizeAndTrack 成功時應回傳文字並記錄用量")
    void recognizeAndTrackShouldReturnTextAndTrack() throws Exception {
        // Arrange
        SttResponseVo feignResponse = new SttResponseVo();
        feignResponse.setText(" 測試文字 ");
        feignResponse.setLanguage("zh");

        when(aiPyServiceFeignClient.recognize(any(), any(), any())).thenReturn(feignResponse);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/user-audio/presigned-url");

        // Act
        String text = sttService.recognizeAndTrack(new byte[]{1, 2, 3}, "zh", "test-service");

        // Assert
        assertEquals("測試文字", text);
        verify(usageTrackService, times(1)).track(eq("test-service"), eq("stt"), eq("file"), eq(1L));
    }

    @Test
    @DisplayName("recognize 指定 provider 時應透傳給 Feign")
    void recognizeShouldPassProviderToFeign() throws Exception {
        // Arrange
        SttResponseVo feignResponse = new SttResponseVo();
        feignResponse.setText("你好");
        feignResponse.setLanguage("zh");

        when(aiPyServiceFeignClient.recognize(any(), any(), any())).thenReturn(feignResponse);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/user-audio/presigned-url");

        // Act
        sttService.recognize(new byte[]{1, 2, 3}, "zh", "sensevoice");

        // Assert
        verify(aiPyServiceFeignClient).recognize(any(), eq("zh"), eq("sensevoice"));
    }

    @Test
    @DisplayName("recognize 未指定 provider 時應傳 null")
    void recognizeShouldPassNullProviderWhenNotSpecified() throws Exception {
        // Arrange
        SttResponseVo feignResponse = new SttResponseVo();
        feignResponse.setText("你好");
        feignResponse.setLanguage("zh");

        when(aiPyServiceFeignClient.recognize(any(), any(), any())).thenReturn(feignResponse);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/user-audio/presigned-url");

        // Act
        sttService.recognize(new byte[]{1, 2, 3}, "zh");

        // Assert
        verify(aiPyServiceFeignClient).recognize(any(), eq("zh"), isNull());
    }
}
