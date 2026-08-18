package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Vo.AiJobPostingVo;
import com.example.BackendArchitectureLab.Service.IAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai/inner")
@RequiredArgsConstructor
public class AiInternalController {

    private final IAiService aiService;

    @PostMapping("/analyze-jobs")
    public List<AiJobPostingVo> analyzeJobPostings(@RequestParam("companyName") String companyName,
                                                    @RequestParam("htmlContent") String htmlContent) {
        return aiService.analyzeJobPostings(companyName, htmlContent);
    }
}
