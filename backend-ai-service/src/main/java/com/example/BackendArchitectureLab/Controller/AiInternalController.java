package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Dto.Vo.AiJobPostingDto;
import com.example.BackendArchitectureLab.Service.IAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai/inner")
public class AiInternalController {

    @Autowired
    private IAiService aiService;

    @PostMapping("/analyze-jobs")
    public List<AiJobPostingDto> analyzeJobPostings(@RequestParam("companyName") String companyName,
                                                    @RequestParam("htmlContent") String htmlContent) {
        return aiService.analyzeJobPostings(companyName, htmlContent);
    }
}
