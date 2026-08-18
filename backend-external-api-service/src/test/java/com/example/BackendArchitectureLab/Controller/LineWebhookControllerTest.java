package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.ILineDiaryService;
import com.example.BackendArchitectureLab.Service.ILineGfService;
import com.example.BackendArchitectureLab.Service.ILineWebhookService;
import com.example.BackendArchitectureLab.Service.ITtsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LineWebhookControllerTest {

    @Mock
    private ILineGfService lineGfService;

    @Mock
    private ILineDiaryService lineDiaryService;

    @Mock
    private ILineWebhookService lineWebhookService;

    @Mock
    private ITtsService ttsService;

    @InjectMocks
    private LineWebhookController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "gfSecret", "mock-gf-secret");
        ReflectionTestUtils.setField(controller, "diarySecret", "mock-diary-secret");
    }

    @Test
    @DisplayName("gfCallback 在 Secret 未設定時應回傳 404")
    void gfCallback_ShouldReturn404_WhenSecretBlank() {
        ReflectionTestUtils.setField(controller, "gfSecret", "");
        ResponseEntity<String> response = controller.gfCallback("{}".getBytes(), "signature");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("GF LINE bot not configured", response.getBody());
    }

    @Test
    @DisplayName("gfCallback 在有效簽章下應成功分發事件並回傳 200")
    void gfCallback_ShouldReturn200_WhenValid() throws Exception {
        byte[] body = "{}".getBytes();
        when(lineWebhookService.parseEvents(eq("mock-gf-secret"), eq(body), eq("valid-sig")))
                .thenReturn(List.of());

        ResponseEntity<String> response = controller.gfCallback(body, "valid-sig");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ok", response.getBody());
        verify(lineWebhookService).dispatchEvents(any(), eq(lineGfService));
    }

    @Test
    @DisplayName("gfCallback 解析失敗應回傳 400")
    void gfCallback_ShouldReturn400_WhenParseException() throws Exception {
        byte[] body = "invalid".getBytes();
        when(lineWebhookService.parseEvents(eq("mock-gf-secret"), eq(body), eq("bad-sig")))
                .thenThrow(new RuntimeException("Invalid signature"));

        ResponseEntity<String> response = controller.gfCallback(body, "bad-sig");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Parse failed"));
    }

    @Test
    @DisplayName("diaryCallback 在有效簽章下應成功分發事件並回傳 200")
    void diaryCallback_ShouldReturn200_WhenValid() throws Exception {
        byte[] body = "{}".getBytes();
        when(lineWebhookService.parseEvents(eq("mock-diary-secret"), eq(body), eq("valid-sig")))
                .thenReturn(List.of());

        ResponseEntity<String> response = controller.diaryCallback(body, "valid-sig");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ok", response.getBody());
        verify(lineWebhookService).dispatchEvents(any(), eq(lineDiaryService));
    }

    @Test
    @DisplayName("streamAudio 成功下載音訊檔案應回傳 200 與 ContentType")
    void streamAudio_ShouldReturn200_WhenFound() throws Exception {
        byte[] audioBytes = new byte[]{1, 2, 3};
        when(ttsService.downloadTtsFile("sample.wav")).thenReturn(audioBytes);

        ResponseEntity<byte[]> response = controller.streamAudio("sample.wav");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(audioBytes, response.getBody());
        assertEquals("audio/x-wav", response.getHeaders().getContentType().toString());
    }

    @Test
    @DisplayName("streamAudio 找不到檔案或失敗應回傳 404")
    void streamAudio_ShouldReturn404_WhenException() throws Exception {
        when(ttsService.downloadTtsFile("notfound.wav")).thenThrow(new RuntimeException("File not found"));

        ResponseEntity<byte[]> response = controller.streamAudio("notfound.wav");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
