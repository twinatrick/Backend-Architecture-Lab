package com.example.BackendArchitectureLab.Feign;

import com.example.BackendArchitectureLab.Dto.Vo.AiJobPostingDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "ai-service")
public interface AiServiceFeignClient {

    @PostMapping("/ai/inner/analyze-jobs")
    List<AiJobPostingDto> analyzeJobPostings(@RequestParam("companyName") String companyName,
                                             @RequestParam("htmlContent") String htmlContent);
}
