package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Vo.AudioRecognizeVo;
import com.example.BackendArchitectureLab.Vo.SttResponseVo;
import com.example.BackendArchitectureLab.Service.ISttService;
import com.example.BackendArchitectureLab.Service.Nlp.PhoneticConvertService;
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
class LearnServiceTest {

    @Mock
    private ISttService sttService;

    @Mock
    private PhoneticConvertService phoneticConvertService;

    @Mock
    private MultipartFile mockFile;

    @InjectMocks
    private LearnService learnService;

    private SttResponseVo defaultSttResponse;

    @BeforeEach
    void setUp() throws Exception {
        defaultSttResponse = new SttResponseVo();
        defaultSttResponse.setText("你好世界");
        defaultSttResponse.setLanguage("zh");

        when(mockFile.getBytes()).thenReturn(new byte[]{1, 2, 3});
        when(sttService.recognize(any(), any())).thenReturn(defaultSttResponse);
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
    @DisplayName("STT 服務異常或讀取 byte 失敗時應回傳錯誤訊息")
    void shouldHandleSttError() throws Exception {
        when(mockFile.getBytes()).thenThrow(new RuntimeException("讀取音訊檔案失敗"));

        AudioRecognizeVo vo = learnService.processAudio(mockFile, "zh", "pinyin");

        assertNotNull(vo);
        assertNotNull(vo.getText());
        assertTrue(vo.getText().contains("音訊處理失敗"));
    }

    @Test
    @DisplayName("STT 服務回傳日文文字時應正確傳遞")
    void shouldPassThroughMockText() {
        SttResponseVo jaResponse = new SttResponseVo();
        jaResponse.setText("こんにちは");
        jaResponse.setLanguage("ja");

        when(sttService.recognize(any(), eq("ja"))).thenReturn(jaResponse);
        when(phoneticConvertService.convert(any(), any(), any())).thenReturn("konnichiwa");

        AudioRecognizeVo vo = learnService.processAudio(mockFile, "ja", "romaji");

        assertEquals("こんにちは", vo.getText());
        assertEquals("konnichiwa", vo.getPhonetic());
    }
}
