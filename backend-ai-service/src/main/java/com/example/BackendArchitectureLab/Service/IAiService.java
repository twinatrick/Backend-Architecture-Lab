package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Vo.AiJobPostingDto;
import java.util.List;

public interface IAiService {

    List<AiJobPostingDto> analyzeJobPostings(String companyName, String htmlContent);
}
