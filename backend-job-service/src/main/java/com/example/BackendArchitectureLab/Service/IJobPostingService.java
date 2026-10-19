package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Dto.Request.CreateJobPostingRequest;
import com.example.BackendArchitectureLab.Vo.JobPostingVo;
import com.example.BackendArchitectureLab.Dto.Query.JobPostingSearchQuery;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

public interface IJobPostingService {

    JobPostingVo createJobPosting(CreateJobPostingRequest request);

    Flux<ServerSentEvent<List<JobPostingVo>>> streamJobPostingsChunked(int chunkSize);

    JobPostingVo getJobPostingById(String id);

    BatchResultVo<JobPostingVo> getJobPostingsByIds(List<UUID> ids);

    JobPostingVo updateJobPosting(JobPostingVo jobPostingVo);

    void deleteJobPosting(String id);

    List<JobPostingVo> getJobPostingsByCompanyId(String companyId);
    CacheListWrapper<JobPostingVo> getJobPostingsByCompanyIdCache(String companyId);

    List<JobPostingVo> scrapeAndAnalyzeJobs(String companyId);

    void scrapeAndAnalyzeAllCompanies();

    PageResult<JobPostingVo> searchJobPostings(JobPostingSearchQuery query);
}
