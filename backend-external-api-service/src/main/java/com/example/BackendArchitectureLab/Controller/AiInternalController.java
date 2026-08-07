package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Vo.AiJobPostingVo;
import com.example.BackendArchitectureLab.Service.IAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai/inner")
public class AiInternalController {

    @Autowired
    private IAiService aiService;

    @PostMapping("/analyze-jobs")
    public List<AiJobPostingVo> analyzeJobPostings(@RequestParam("companyName") String companyName,
                                                    @RequestParam("htmlContent") String htmlContent) {
        return aiService.analyzeJobPostings(companyName, htmlContent);
    }
}
