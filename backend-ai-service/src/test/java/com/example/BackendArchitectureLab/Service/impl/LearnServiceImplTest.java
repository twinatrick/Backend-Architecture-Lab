package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.AudioRecognizeVo;
import com.example.BackendArchitectureLab.Service.Nlp.PhoneticConvertService;
import com.example.BackendArchitectureLab.Service.Onnx.WhisperOnnxService;
import com.example.BackendArchitectureLab.Util.AudioProcessUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearnServiceImplTest {

    @Mock
    private AudioProcessUtil audioProcessUtil;

    @Mock
    private WhisperOnnxService whisperOnnxService;

    @Mock
    private PhoneticConvertService phoneticConvertService;

    @Mock
    private MultipartFile mockFile;

    @InjectMocks
    private LearnServiceImpl learnService;

    @BeforeEach
    void setUp() throws Exception {
        when(audioProcessUtil.convertTo16kMonoFloatArray(any())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(whisperOnnxService.transcribe(any(), any())).thenReturn("你好世界");
        when(phoneticConvertService.convert(any(), any(), any())).thenReturn("nǐ hǎo shì jiè");
    }

    @Test
    @DisplayName("processAudio 應正確回傳 AudioRecognizeVo")
    void shouldProcessAudioSuccessfully() {
        AudioRecognizeVo vo = learnService.processAudio(mockFile, "zh", "pinyin");

        assertNotNull(vo);
        assertEquals("你好世界", vo.getText());
        assertEquals("nǐ hǎo shì jiè", vo.getPhonetic());
    }

    @Test
    @DisplayName("mode=none 時 phonetic 應為 null")
    void shouldReturnNullPhoneticWhenModeIsNone() {
        when(phoneticConvertService.convert(any(), eq("none"), any())).thenReturn(null);

        AudioRecognizeVo vo = learnService.processAudio(mockFile, "zh", "none");

        assertNotNull(vo);
        assertEquals("你好世界", vo.getText());
        assertNull(vo.getPhonetic());
    }

    @Test
    @DisplayName("音訊轉檔失敗時應回傳錯誤訊息")
    void shouldHandleAudioConversionError() throws Exception {
        when(audioProcessUtil.convertTo16kMonoFloatArray(any()))
                .thenThrow(new RuntimeException("轉檔失敗"));

        AudioRecognizeVo vo = learnService.processAudio(mockFile, "zh", "pinyin");

        assertNotNull(vo);
        assertNotNull(vo.getText());
        assertTrue(vo.getText().contains("音訊處理失敗"));
    }

    @Test
    @DisplayName("Whisper 回傳 mock 文字時應正確傳遞")
    void shouldPassThroughMockText() {
        when(whisperOnnxService.transcribe(any(), eq("ja"))).thenReturn("こんにちは");
        when(phoneticConvertService.convert(any(), any(), any())).thenReturn("konnichiwa");

        AudioRecognizeVo vo = learnService.processAudio(mockFile, "ja", "romaji");

        assertEquals("こんにちは", vo.getText());
        assertEquals("konnichiwa", vo.getPhonetic());
    }
}
