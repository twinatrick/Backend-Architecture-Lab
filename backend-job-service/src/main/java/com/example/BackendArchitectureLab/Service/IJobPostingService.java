package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Dto.Vo.CreateJobPostingRequest;
import com.example.BackendArchitectureLab.Dto.Vo.JobPostingVo;
import com.example.BackendArchitectureLab.Dto.Vo.Search.JobPostingSearchQuery;

import java.util.List;

public interface IJobPostingService {

    JobPostingVo createJobPosting(CreateJobPostingRequest request);

    List<JobPostingVo> getAllJobPostings();

    JobPostingVo getJobPostingById(String id);

    JobPostingVo updateJobPosting(JobPostingVo jobPostingVo);

    void deleteJobPosting(String id);

    List<JobPostingVo> getJobPostingsByCompanyId(String companyId);

    List<JobPostingVo> scrapeAndAnalyzeJobs(String companyId);

    void scrapeAndAnalyzeAllCompanies();

    PageResult<JobPostingVo> searchJobPostings(JobPostingSearchQuery query);
}
