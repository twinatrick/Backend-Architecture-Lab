package com.example.BackendArchitectureLab.Service.Onnx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class WhisperOnnxServiceTest {

    private WhisperOnnxService service;

    @BeforeEach
    void setUp() {
        service = new WhisperOnnxService();
    }

    @Test
    @DisplayName("模型未就緒時應回傳模擬繁體中文文字")
    void shouldReturnMockChineseWhenModelNotReady() {
        String result = service.transcribe(new float[]{0.1f, 0.2f}, "zh");
        assertEquals("這是一段模擬的語音辨識結果。", result);
    }

    @Test
    @DisplayName("模型未就緒時應回傳模擬日文文字")
    void shouldReturnMockJapaneseWhenModelNotReady() {
        String result = service.transcribe(new float[]{0.1f, 0.2f}, "ja");
        assertEquals("こんにちは、これはテストです。", result);
    }

    @Test
    @DisplayName("預設語言應回傳模擬繁體中文")
    void shouldDefaultToChinese() {
        String result = service.transcribe(new float[]{0.1f, 0.2f}, "en");
        assertEquals("這是一段模擬的語音辨識結果。", result);
    }

    @Test
    @DisplayName("空的音訊陣列不應拋出例外")
    void shouldHandleEmptyAudioGracefully() {
        String result = service.transcribe(new float[0], "zh");
        assertNotNull(result);
    }
}
