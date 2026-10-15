package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import org.springframework.context.annotation.Lazy;
import com.example.BackendArchitectureLab.Crawler.Impl.CompositeJobCrawler;
import com.example.BackendArchitectureLab.DataAccess.ICompanyDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IJobPostingDataAccess;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Vo.AiJobPostingVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.CreateJobPostingRequest;
import com.example.BackendArchitectureLab.Vo.JobPostingVo;
import com.example.BackendArchitectureLab.Vo.Search.JobPostingSearchQuery;
import com.example.BackendArchitectureLab.Entity.Company;
import com.example.BackendArchitectureLab.Entity.CompanyWebsite;
import com.example.BackendArchitectureLab.Entity.JobPosting;
import com.example.BackendArchitectureLab.Feign.ExternalApiServiceFeignClient;
import com.example.BackendArchitectureLab.Mapper.JobPostingMapper;
import com.example.BackendArchitectureLab.Service.IJobPostingService;
import com.example.BackendArchitectureLab.Util.BatchCacheHelper;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobPostingService implements IJobPostingService {

    private final IJobPostingDataAccess jobPostingDataAccess;
    private final ICompanyDataAccess companyDataAccess;
    private final JobPostingMapper jobPostingMapper;
    private final CompositeJobCrawler jobCrawler;
    private final ExternalApiServiceFeignClient aiServiceFeignClient;
    private final CacheManager cacheManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final TransactionExecutor transactionExecutor;

    @Lazy
    private final IJobPostingService self;

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
        evictJobPostingsSearchCache();
        return jobPostingMapper.toVo(jobPosting);
    }

    @Override
    public Flux<ServerSentEvent<List<JobPostingVo>>> streamJobPostingsChunked(int chunkSize) {
        final int effectiveChunkSize = Math.max(10, Math.min(chunkSize, 1000));

        Flux<ServerSentEvent<List<JobPostingVo>>> dataFlux = Flux.<ServerSentEvent<List<JobPostingVo>>, Integer>generate(
                () -> 0,
                (page, sink) -> {
                    try {
                        Page<JobPostingVo> jobPostingPage = transactionExecutor.executeReadOnly(() -> {
                            Page<JobPosting> entityPage = jobPostingDataAccess.findAllPaged(
                                    PageRequest.of(page, effectiveChunkSize, Sort.by("id").ascending())
                            );

                            if (entityPage.isEmpty()) {
                                return new PageImpl<>(List.<JobPostingVo>of(), entityPage.getPageable(), entityPage.getTotalElements());
                            }

                            List<JobPostingVo> voList = entityPage.getContent().stream()
                                    .map(jobPostingMapper::toVo)
                                    .toList();

                            return new PageImpl<>(voList, entityPage.getPageable(), entityPage.getTotalElements());
                        });

                        if (jobPostingPage.isEmpty()) {
                            sink.complete();
                            return page;
                        }

                        sink.next(ServerSentEvent.<List<JobPostingVo>>builder()
                                .event("chunk")
                                .id(String.valueOf(page))
                                .data(jobPostingPage.getContent())
                                .build());

                        if (!jobPostingPage.hasNext()) {
                            sink.complete();
                            return page;
                        }

                        return page + 1;
                    } catch (Exception ex) {
                        log.error("職缺 SSE 分塊串流分頁查詢失敗 [page={}]: {}", page, ex.getMessage(), ex);
                        sink.error(ex);
                        return page;
                    }
                }
        ).subscribeOn(Schedulers.boundedElastic());

        ServerSentEvent<List<JobPostingVo>> completeEvent = ServerSentEvent.<List<JobPostingVo>>builder()
                .event("complete")
                .data(List.of())
                .build();

        Flux<ServerSentEvent<List<JobPostingVo>>> streamWithComplete = Flux.concat(dataFlux, Flux.just(completeEvent));

        Flux<ServerSentEvent<List<JobPostingVo>>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<List<JobPostingVo>>builder()
                        .comment("keep-alive")
                        .build());

        return Flux.merge(streamWithComplete, heartbeatFlux)
                .takeUntil(event -> "complete".equals(event.event()))
                .doOnCancel(() -> log.info("客戶端中斷職缺 SSE 串流連線"))
                .onErrorResume(ex -> {
                    log.error("職缺 SSE 串流處理發生異常: {}", ex.getMessage(), ex);
                    return Flux.just(
                            ServerSentEvent.<List<JobPostingVo>>builder()
                                    .event("error")
                                    .comment("串流處理發生異常，請聯繫管理員")
                                    .build()
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "jobPostings", key = "#id", sync = true)
    public JobPostingVo getJobPostingById(String id) {
        UUID uuid = mapUuid(id);
        if (uuid == null) {
            throw new AppException("NOT_FOUND", "職缺不存在", 404);
        }
        JobPosting jobPosting = jobPostingDataAccess.findById(uuid)
                .orElseThrow(() -> new AppException("NOT_FOUND", "職缺不存在", 404));
        return jobPostingMapper.toVo(jobPosting);
    }

    @Override
    @Transactional(readOnly = true)
    public BatchResultVo<JobPostingVo> getJobPostingsByIds(List<UUID> ids) {
        return BatchCacheHelper.fetchBatchWithCache(
                cacheManager,
                "jobPostings",
                ids,
                missingIds -> jobPostingDataAccess.findAllById(new ArrayList<>(missingIds)).stream()
                        .map(jobPostingMapper::toVo)
                        .toList(),
                vo -> vo.getId() != null ? UUID.fromString(vo.getId()) : null
        );
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "jobPostings", key = "#jobPostingVo.id"),
        @CacheEvict(value = "jobPostings", key = "T(java.util.UUID).fromString(#jobPostingVo.id)"),
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
        evictJobPostingsSearchCache();
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
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache("jobPostings");
            if (cache != null) {
                cache.evict(id);
                cache.evict(uuid);
                cache.evict("bycompany:" + companyId);
            }
        }
        evictJobPostingsSearchCache();
    }

    @Override
    public List<JobPostingVo> getJobPostingsByCompanyId(String companyId) {
        return self.getJobPostingsByCompanyIdCache(companyId).getData();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "jobPostings", key = "'bycompany:' + #companyId", sync = true)
    public CacheListWrapper<JobPostingVo> getJobPostingsByCompanyIdCache(String companyId) {
        UUID uuid = mapUuid(companyId);
        if (uuid == null) {
            throw new IllegalArgumentException("Company ID must not be null");
        }
        List<JobPostingVo> list = jobPostingDataAccess.findByCompanyId(uuid).stream()
                .map(jobPostingMapper::toVo)
                .toList();
        return new CacheListWrapper<>(list);
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "jobPostings", key = "'bycompany:' + #companyId"),
        @CacheEvict(value = "companies", key = "'all'")
    })
    public List<JobPostingVo> scrapeAndAnalyzeJobs(String companyId) {
        UUID uuid = mapUuid(companyId);
        if (uuid == null) {
            throw new IllegalArgumentException("Company ID must not be null");
        }

        var companyInfo = transactionExecutor.executeReadOnly(() -> {
            Company company = companyDataAccess.findById(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Company not found"));
            List<String> urls = company.getWebsites() == null ? List.<String>of() :
                    company.getWebsites().stream().map(CompanyWebsite::getUrl).toList();
            return Map.entry(company.getName(), urls);
        });

        String companyName = companyInfo.getKey();
        List<String> websiteUrls = companyInfo.getValue();

        if (websiteUrls.isEmpty()) {
            log.warn("Company {} has no websites to scrape", companyName);
            return List.of();
        }

        log.info("Starting job scraping for company: {} ({} websites)", companyName, websiteUrls.size());

        List<JobPostingVo> allSavedJobs = new ArrayList<>();

        for (String url : websiteUrls) {
            log.info("Crawling website: {}", url);

            // 1. 外部慢速爬蟲 I/O 位於資料庫交易外部執行，避免長時間霸佔連線池
            String htmlContent;
            try {
                htmlContent = jobCrawler.crawl(url);
            } catch (Exception e) {
                log.warn("Failed to crawl URL {}: {}", url, e.toString());
                continue;
            }

            // 2. 外部慢速 AI 推理 Feign 呼叫位於資料庫交易外部執行
            List<AiJobPostingVo> analyzedJobs = aiServiceFeignClient.analyzeJobPostings(companyName, htmlContent);
            log.info("AI service returned {} jobs from URL: {}", analyzedJobs.size(), url);

            // 3. 資料庫寫入使用獨立短事務執行
            List<JobPostingVo> savedForWebsite = transactionExecutor.executeWritable(() -> {
                Company managedCompany = companyDataAccess.findById(uuid)
                        .orElseThrow(() -> new IllegalArgumentException("Company not found"));
                List<JobPostingVo> batchSaved = new ArrayList<>();
                List<JobPosting> existingJobs = jobPostingDataAccess.findByCompanyId(uuid);

                for (AiJobPostingVo jobData : analyzedJobs) {
                    try {
                        String title = jobData.getTitle() != null && !jobData.getTitle().isBlank() ? jobData.getTitle() : "Unknown Title";
                        String salaryRange = jobData.getSalaryRange() != null ? jobData.getSalaryRange() : "";

                        JobPosting matched = findMatch(existingJobs, title, salaryRange);
                        if (matched != null) {
                            boolean updated = updateIfChanged(matched, jobData);
                            if (updated) {
                                jobPostingDataAccess.save(matched);
                                batchSaved.add(jobPostingMapper.toVo(matched));
                                log.info("Updated job: {} for company: {}", title, companyName);
                            }
                            continue;
                        }

                        JobPosting jobPosting = new JobPosting();
                        jobPosting.setCompany(managedCompany);
                        jobPosting.setTitle(title);
                        jobPosting.setUrl(jobData.getUrl() != null ? jobData.getUrl() : "");
                        jobPosting.setDescription(jobData.getDescription() != null ? jobData.getDescription() : "");
                        jobPosting.setRequirements(jobData.getRequirements() != null ? jobData.getRequirements() : "");
                        jobPosting.setResponsibilities(jobData.getResponsibilities() != null ? jobData.getResponsibilities() : "");
                        jobPosting.setSalaryRange(salaryRange);

                        jobPosting = jobPostingDataAccess.save(jobPosting);
                        batchSaved.add(jobPostingMapper.toVo(jobPosting));
                        log.info("Created new job: {} for company: {}", title, companyName);
                    } catch (Exception e) {
                        log.error("Failed to process job posting for company: {}: {}", companyName, e.toString());
                    }
                }
                return batchSaved;
            });
            allSavedJobs.addAll(savedForWebsite);
        }

        transactionExecutor.executeWritable(() -> {
            Company companyToUpdate = companyDataAccess.findById(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Company not found"));
            companyToUpdate.setLastScrapedAt(LocalDate.now());
            companyDataAccess.save(companyToUpdate);
            return null;
        });
        if (cacheManager != null) {
            Cache companiesCache = cacheManager.getCache("companies");
            if (companiesCache != null) {
                companiesCache.evict(uuid.toString());
            }
        }

        evictJobPostingsSearchCache();
        log.info("Completed scraping for company: {}, total saved/updated: {}", companyName, allSavedJobs.size());
        return allSavedJobs;
    }

    @Override
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
    @Transactional(readOnly = true)
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

    private boolean updateIfChanged(JobPosting existing, AiJobPostingVo newData) {
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

    private void evictJobPostingsSearchCache() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            Set<String> keys = stringRedisTemplate.keys("jobPostings::*search:*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Failed to evict jobPostings search cache", e);
        }
    }

    private UUID mapUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }
}
