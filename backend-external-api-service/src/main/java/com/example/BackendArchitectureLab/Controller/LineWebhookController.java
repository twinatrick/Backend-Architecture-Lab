package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.Ignore;
import com.example.BackendArchitectureLab.Service.ILineDiaryService;
import com.example.BackendArchitectureLab.Service.ILineGfService;
import com.example.BackendArchitectureLab.Service.ILineWebhookService;
import com.example.BackendArchitectureLab.Service.ITtsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LineWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LineWebhookController.class);

    private final ILineGfService lineGfService;
    private final ILineDiaryService lineDiaryService;
    private final ILineWebhookService lineWebhookService;
    private final ITtsService ttsService;

    @Value("${line.gf.channel-secret:}")
    private String gfSecret;

    @Value("${line.diary.channel-secret:}")
    private String diarySecret;

    @Ignore
    @PostMapping("/external/line/callback")
    public ResponseEntity<String> gfCallback(
            @RequestBody byte[] bodyBytes,
            @RequestHeader("X-Line-Signature") String signature) {
        if (gfSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("GF LINE bot not configured");
        }
        log.info("gfCallback received, body length={}, signature present={}",
                bodyBytes != null ? bodyBytes.length : 0, signature != null && !signature.isBlank());
        try {
            var events = lineWebhookService.parseEvents(gfSecret, bodyBytes, signature);
            log.info("gfCallback parsed {} events", events.size());
            lineWebhookService.dispatchEvents(events, lineGfService);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.warn("gfCallback parse failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Parse failed: " + e.getMessage());
        }
    }

    @Ignore
    @PostMapping("/external/line/diary/callback")
    public ResponseEntity<String> diaryCallback(
            @RequestBody byte[] bodyBytes,
            @RequestHeader("X-Line-Signature") String signature) {
        if (diarySecret.isBlank() || lineDiaryService == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Diary LINE bot not configured");
        }
        log.info("diaryCallback received, body length={}, signature present={}",
                bodyBytes != null ? bodyBytes.length : 0, signature != null && !signature.isBlank());
        try {
            var events = lineWebhookService.parseEvents(diarySecret, bodyBytes, signature);
            log.info("diaryCallback parsed {} events", events.size());
            lineWebhookService.dispatchEvents(events, lineDiaryService);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.warn("diaryCallback parse failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Parse failed: " + e.getMessage());
        }
    }

    @Ignore
    @GetMapping("/external/public/audio/stream/{fileName}")
    public ResponseEntity<byte[]> streamAudio(@PathVariable("fileName") String fileName) {
        log.info("streamAudio requested for fileName={}", fileName);
        try {
            byte[] data = ttsService.downloadTtsFile(fileName);
            HttpHeaders headers = new HttpHeaders();
            
            String contentType = "audio/x-wav";
            if (fileName != null && fileName.endsWith(".m4a")) {
                contentType = "audio/mp4";
            }
            
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(data.length);
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"");
            return new ResponseEntity<>(data, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.warn("streamAudio failed for file: {}, error: {}", fileName, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
