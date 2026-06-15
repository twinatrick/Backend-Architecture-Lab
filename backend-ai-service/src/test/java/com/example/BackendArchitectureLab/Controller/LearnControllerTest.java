package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Dto.Vo.AudioRecognizeVo;
import com.example.BackendArchitectureLab.Dto.Vo.ResponseType;
import com.example.BackendArchitectureLab.Service.ILearnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearnControllerTest {

    @Mock
    private ILearnService learnService;

    @InjectMocks
    private LearnController controller;

    private MockMultipartFile mockFile;

    @BeforeEach
    void setUp() {
        mockFile = new MockMultipartFile("file", "test.mp3", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{0x01, 0x02});
    }

    @Test
    @DisplayName("recognizeAudio 應回傳成功 ResponseType")
    void shouldRecognizeAudioSuccessfully() {
        AudioRecognizeVo vo = AudioRecognizeVo.builder()
                .text("你好世界")
                .phonetic("nǐ hǎo shì jiè")
                .build();
        when(learnService.processAudio(any(), eq("zh"), eq("pinyin"))).thenReturn(vo);

        ResponseType<AudioRecognizeVo> result = controller.recognizeAudio("zh", "pinyin", mockFile);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("你好世界", result.getData().getText());
        assertEquals("nǐ hǎo shì jiè", result.getData().getPhonetic());
    }

    @Test
    @DisplayName("空白檔案應回傳失敗 ResponseType")
    void shouldReturnFailForEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.mp3", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[0]);

        ResponseType<AudioRecognizeVo> result = controller.recognizeAudio("zh", "pinyin", emptyFile);

        assertNotNull(result);
        assertEquals(400, result.getCode());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("不同語言模式應正確傳遞參數")
    void shouldPassLanguageAndModeCorrectly() {
        AudioRecognizeVo vo = AudioRecognizeVo.builder()
                .text("こんにちは")
                .build();
        when(learnService.processAudio(any(), eq("ja"), eq("romaji"))).thenReturn(vo);

        ResponseType<AudioRecognizeVo> result = controller.recognizeAudio("ja", "romaji", mockFile);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("こんにちは", result.getData().getText());
        verify(learnService).processAudio(any(), eq("ja"), eq("romaji"));
    }
}
