package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.Ingnore;
import com.example.BackendArchitectureLab.Service.ILineDiaryService;
import com.example.BackendArchitectureLab.Service.ILineGfService;
import com.example.BackendArchitectureLab.Service.ILineWebhookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LineWebhookController {

    @Autowired
    private ILineGfService lineGfService;

    @Autowired
    private ILineDiaryService lineDiaryService;

    @Autowired
    private ILineWebhookService lineWebhookService;

    @Value("${line.gf.channel-secret:}")
    private String gfSecret;

    @Value("${line.diary.channel-secret:}")
    private String diarySecret;

    @Ingnore
    @PostMapping("/external/line/callback")
    public ResponseEntity<String> gfCallback(
            @RequestBody String body,
            @RequestHeader("X-Line-Signature") String signature) {
        if (gfSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("GF LINE bot not configured");
        }
        try {
            var events = lineWebhookService.parseEvents(gfSecret, body, signature);
            lineWebhookService.dispatchEvents(events, lineGfService);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Parse failed: " + e.getMessage());
        }
    }

    @Ingnore
    @PostMapping("/external/line/diary/callback")
    public ResponseEntity<String> diaryCallback(
            @RequestBody String body,
            @RequestHeader("X-Line-Signature") String signature) {
        if (diarySecret.isBlank() || lineDiaryService == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Diary LINE bot not configured");
        }
        try {
            var events = lineWebhookService.parseEvents(diarySecret, body, signature);
            lineWebhookService.dispatchEvents(events, lineDiaryService);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Parse failed: " + e.getMessage());
        }
    }
}
