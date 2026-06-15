package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Crawler.impl.CompositeJobCrawler;
import com.example.BackendArchitectureLab.DataAccess.ICompanyDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IJobPostingDataAccess;
import com.example.BackendArchitectureLab.Dto.Vo.AiJobPostingDto;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Dto.Vo.CreateJobPostingRequest;
import com.example.BackendArchitectureLab.Dto.Vo.JobPostingVo;
import com.example.BackendArchitectureLab.Dto.Vo.Search.JobPostingSearchQuery;
import com.example.BackendArchitectureLab.Entity.Company;
import com.example.BackendArchitectureLab.Entity.JobPosting;
import com.example.BackendArchitectureLab.Feign.AiServiceFeignClient;
import com.example.BackendArchitectureLab.Mapper.JobPostingMapper;
import com.example.BackendArchitectureLab.Service.IJobPostingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class JobPostingService implements IJobPostingService {

    @Autowired
    private IJobPostingDataAccess jobPostingDataAccess;
    @Autowired
    private ICompanyDataAccess companyDataAccess;
    @Autowired
    private JobPostingMapper jobPostingMapper;
    @Autowired
    private CompositeJobCrawler jobCrawler;
    @Autowired
    private AiServiceFeignClient aiServiceFeignClient;
    @Autowired
    private CacheManager cacheManager;

    @Override
    @Transactional
    @Caching(put = {
        @CachePut(value = "jobPostings", key = "#result.id")
    }, evict = {
        @CacheEvict(value = "jobPostings", key = "'bycompany:' + #request.companyId")
    })
    public JobPostingVo createJobPosting(CreateJobPostingRequest request) {
        Company company = companyDataAccess.findById(UUID.fromString(request.getCompanyId()))
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        JobPosting jobPosting = new JobPosting();
        jobPosting.setCompany(company);
        jobPosting.setTitle(request.getTitle());
        jobPosting.setUrl(request.getUrl());
        jobPosting.setDescription(request.getDescription());
        jobPosting.setRequirements(request.getRequirements());
        jobPosting.setResponsibilities(request.getResponsibilities());
        jobPosting.setSalaryRange(request.getSalaryRange());
        jobPosting.setPostedDate(request.getPostedDate());
        jobPosting = jobPostingDataAccess.save(jobPosting);
        return jobPostingMapper.toVo(jobPosting);
    }

    @Override
    @Cacheable(value = "jobPostings", sync = true)
    public List<JobPostingVo> getAllJobPostings() {
        return jobPostingDataAccess.findAll().stream()
                .map(jobPostingMapper::toVo)
                .toList();
    }

    @Override
    @Cacheable(value = "jobPostings", key = "#id", sync = true)
    public JobPostingVo getJobPostingById(String id) {
        UUID uuid = mapUuid(id);
        if (uuid == null) {
            throw new IllegalArgumentException("ID must not be null");
        }
        JobPosting jobPosting = jobPostingDataAccess.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Job posting not found"));
        return jobPostingMapper.toVo(jobPosting);
    }

    @Override
    @Transactional
    @Caching(put = {
        @CachePut(value = "jobPostings", key = "#jobPostingVo.id")
    }, evict = {
        @CacheEvict(value = "jobPostings", key = "'bycompany:' + #jobPostingVo.companyId")
    })
    public JobPostingVo updateJobPosting(JobPostingVo jobPostingVo) {
        if (jobPostingVo.getId() == null || jobPostingVo.getId().isBlank()) {
            throw new IllegalArgumentException("ID must not be null");
        }
        JobPosting jobPosting = jobPostingDataAccess.findById(UUID.fromString(jobPostingVo.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Job posting not found"));

        if (jobPostingVo.getCompanyId() != null) {
            Company company = companyDataAccess.findById(UUID.fromString(jobPostingVo.getCompanyId()))
                    .orElseThrow(() -> new IllegalArgumentException("Company not found"));
            jobPosting.setCompany(company);
        }
        if (jobPostingVo.getTitle() != null) {
            jobPosting.setTitle(jobPostingVo.getTitle());
        }
        if (jobPostingVo.getUrl() != null) {
            jobPosting.setUrl(jobPostingVo.getUrl());
        }
        if (jobPostingVo.getDescription() != null) {
            jobPosting.setDescription(jobPostingVo.getDescription());
        }
        if (jobPostingVo.getRequirements() != null) {
            jobPosting.setRequirements(jobPostingVo.getRequirements());
        }
        if (jobPostingVo.getResponsibilities() != null) {
            jobPosting.setResponsibilities(jobPostingVo.getResponsibilities());
        }
        if (jobPostingVo.getSalaryRange() != null) {
            jobPosting.setSalaryRange(jobPostingVo.getSalaryRange());
        }
        if (jobPostingVo.getPostedDate() != null) {
            jobPosting.setPostedDate(jobPostingVo.getPostedDate());
        }
        if (jobPostingVo.getGeminiAnalysis() != null) {
            jobPosting.setGeminiAnalysis(jobPostingVo.getGeminiAnalysis());
        }

        jobPosting = jobPostingDataAccess.save(jobPosting);
        return jobPostingMapper.toVo(jobPosting);
    }

    @Override
    @Transactional
    public void deleteJobPosting(String id) {
        UUID uuid = mapUuid(id);
        if (uuid == null) {
            throw new IllegalArgumentException("ID must not be null");
        }
        JobPosting jobPosting = jobPostingDataAccess.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Job posting not found"));
        String companyId = jobPosting.getCompany().getId().toString();
        jobPostingDataAccess.deleteById(uuid);
        Cache cache = cacheManager.getCache("jobPostings");
        if (cache != null) {
            cache.evict(id);
            cache.evict("bycompany:" + companyId);
        }
    }

    @Override
    @Cacheable(value = "jobPostings", key = "'bycompany:' + #companyId", sync = true)
    public List<JobPostingVo> getJobPostingsByCompanyId(String companyId) {
        UUID uuid = mapUuid(companyId);
        if (uuid == null) {
            throw new IllegalArgumentException("Company ID must not be null");
        }
        return jobPostingDataAccess.findByCompanyId(uuid).stream()
                .map(jobPostingMapper::toVo)
                .toList();
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "jobPostings", key = "'bycompany:' + #companyId"),
        @CacheEvict(value = "companies", allEntries = true)
    })
    public List<JobPostingVo> scrapeAndAnalyzeJobs(String companyId) {
        UUID uuid = mapUuid(companyId);
        if (uuid == null) {
            throw new IllegalArgumentException("Company ID must not be null");
        }

        Company company = companyDataAccess.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        if (company.getWebsites() == null || company.getWebsites().isEmpty()) {
            log.warn("Company {} has no websites to scrape", company.getName());
            return List.of();
        }

        log.info("Starting job scraping for company: {} ({} websites)", company.getName(), company.getWebsites().size());

        List<JobPostingVo> allSavedJobs = new ArrayList<>();

        for (var companyWebsite : company.getWebsites()) {
            String url = companyWebsite.getUrl();
            log.info("Crawling website: {}", url);

            String htmlContent;
            try {
                htmlContent = jobCrawler.crawl(url);
            } catch (Exception e) {
                log.warn("Failed to crawl URL {}: {}", url, e.toString());
                continue;
            }

            List<AiJobPostingDto> analyzedJobs = aiServiceFeignClient.analyzeJobPostings(company.getName(), htmlContent);
            log.info("AI service returned {} jobs from URL: {}", analyzedJobs.size(), url);

            List<JobPosting> existingJobs = jobPostingDataAccess.findByCompanyId(uuid);

            for (AiJobPostingDto jobData : analyzedJobs) {
                try {
                    String title = jobData.getTitle() != null && !jobData.getTitle().isBlank() ? jobData.getTitle() : "Unknown Title";
                    String salaryRange = jobData.getSalaryRange() != null ? jobData.getSalaryRange() : "";

                    JobPosting matched = findMatch(existingJobs, title, salaryRange);
                    if (matched != null) {
                        boolean updated = updateIfChanged(matched, jobData);
                        if (updated) {
                            jobPostingDataAccess.save(matched);
                            allSavedJobs.add(jobPostingMapper.toVo(matched));
                            log.info("Updated job: {} for company: {}", title, company.getName());
                        }
                        continue;
                    }

                    JobPosting jobPosting = new JobPosting();
                    jobPosting.setCompany(company);
                    jobPosting.setTitle(title);
                    jobPosting.setUrl(jobData.getUrl() != null ? jobData.getUrl() : "");
                    jobPosting.setDescription(jobData.getDescription() != null ? jobData.getDescription() : "");
                    jobPosting.setRequirements(jobData.getRequirements() != null ? jobData.getRequirements() : "");
                    jobPosting.setResponsibilities(jobData.getResponsibilities() != null ? jobData.getResponsibilities() : "");
                    jobPosting.setSalaryRange(salaryRange);

                    jobPosting = jobPostingDataAccess.save(jobPosting);
                    allSavedJobs.add(jobPostingMapper.toVo(jobPosting));
                    log.info("Created new job: {} for company: {}", title, company.getName());
                } catch (Exception e) {
                    log.error("Failed to process job posting for company: {}: {}", company.getName(), e.toString());
                }
            }
        }

        company.setLastScrapedAt(LocalDate.now());
        companyDataAccess.save(company);

        log.info("Completed scraping for company: {}, total saved/updated: {}", company.getName(), allSavedJobs.size());
        return allSavedJobs;
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "jobPostings", allEntries = true),
        @CacheEvict(value = "companies", allEntries = true)
    })
    public void scrapeAndAnalyzeAllCompanies() {
        List<Company> allCompanies = companyDataAccess.findAll();
        LocalDate today = LocalDate.now();

        for (Company company : allCompanies) {
            if (today.equals(company.getLastScrapedAt())) {
                log.info("Skip company {}: already scraped today", company.getName());
                continue;
            }
            try {
                scrapeAndAnalyzeJobs(company.getId().toString());
            } catch (Exception e) {
                log.error("Failed to scrape company: {}", company.getName());
            }
        }
    }

    @Override
    @Cacheable(value = "jobPostings", key = "'search:' + #query.toString()", sync = true)
    public PageResult<JobPostingVo> searchJobPostings(JobPostingSearchQuery query) {
        Page<JobPosting> page = jobPostingDataAccess.searchJobPostings(query);
        List<JobPostingVo> content = page.getContent().stream()
                .map(jobPostingMapper::toVo)
                .toList();
        return PageResult.of(page, content);
    }

    private JobPosting findMatch(List<JobPosting> existingJobs, String title, String salaryRange) {
        if (title == null || title.isBlank()) {
            return null;
        }
        for (JobPosting jp : existingJobs) {
            boolean titleMatch = title.equalsIgnoreCase(jp.getTitle());
            boolean salaryMatch = (salaryRange == null || salaryRange.isBlank())
                    || (jp.getSalaryRange() != null && jp.getSalaryRange().equals(salaryRange));
            if (titleMatch && salaryMatch) {
                return jp;
            }
        }
        return null;
    }

    private boolean updateIfChanged(JobPosting existing, AiJobPostingDto newData) {
        boolean changed = false;
        String newUrl = newData.getUrl() != null ? newData.getUrl() : "";
        if (!newUrl.equals(existing.getUrl() != null ? existing.getUrl() : "")) {
            existing.setUrl(newUrl);
            changed = true;
        }
        String newDesc = newData.getDescription() != null ? newData.getDescription() : "";
        if (!newDesc.equals(existing.getDescription() != null ? existing.getDescription() : "")) {
            existing.setDescription(newDesc);
            changed = true;
        }
        String newReq = newData.getRequirements() != null ? newData.getRequirements() : "";
        if (!newReq.equals(existing.getRequirements() != null ? existing.getRequirements() : "")) {
            existing.setRequirements(newReq);
            changed = true;
        }
        String newResp = newData.getResponsibilities() != null ? newData.getResponsibilities() : "";
        if (!newResp.equals(existing.getResponsibilities() != null ? existing.getResponsibilities() : "")) {
            existing.setResponsibilities(newResp);
            changed = true;
        }
        String newSalary = newData.getSalaryRange() != null ? newData.getSalaryRange() : "";
        if (!newSalary.equals(existing.getSalaryRange() != null ? existing.getSalaryRange() : "")) {
            existing.setSalaryRange(newSalary);
            changed = true;
        }
        return changed;
    }

    private UUID mapUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }
}
