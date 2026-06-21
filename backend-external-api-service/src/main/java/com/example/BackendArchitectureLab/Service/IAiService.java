package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Vo.AiJobPostingVo;
import java.util.List;

public interface IAiService {

    List<AiJobPostingVo> analyzeJobPostings(String companyName, String htmlContent);
}
