package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Vo.AiJobPostingVo;
import com.example.BackendArchitectureLab.Vo.ChatRequestVo;
import com.example.BackendArchitectureLab.Vo.ChatResponseVo;
import com.example.BackendArchitectureLab.Vo.SttResponseVo;
import com.example.BackendArchitectureLab.Vo.TtsRequestVo;
import com.example.BackendArchitectureLab.Vo.TtsResponseVo;
import com.example.BackendArchitectureLab.Service.IAiService;
import com.example.BackendArchitectureLab.Service.IChatService;
import com.example.BackendArchitectureLab.Service.ISttService;
import com.example.BackendArchitectureLab.Service.ITtsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/ai/inner")
public class AiInternalController {

    @Autowired
    private IAiService aiService;

    @Autowired
    private ITtsService ttsService;

    @Autowired
    private IChatService chatService;

    @Autowired
    private ISttService sttService;

    @PostMapping("/analyze-jobs")
    public List<AiJobPostingVo> analyzeJobPostings(@RequestParam("companyName") String companyName,
                                                    @RequestParam("htmlContent") String htmlContent) {
        return aiService.analyzeJobPostings(companyName, htmlContent);
    }

    @PostMapping("/tts/synthesize")
    public TtsResponseVo synthesizeSpeech(@RequestBody TtsRequestVo request) {
        return ttsService.synthesize(request.getText(), request.getLanguage());
    }

    @PostMapping("/chat")
    public ChatResponseVo chat(@RequestBody ChatRequestVo request) {
        return chatService.chat(request.getMessages(), request.getTemperature());
    }

    @PostMapping("/stt/recognize")
    public SttResponseVo recognizeSpeech(@RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "language", defaultValue = "zh") String language) throws IOException {
        return sttService.recognize(file.getBytes(), language);
    }
}
