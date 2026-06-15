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
import com.example.BackendArchitectureLab.Entity.CompanyWebsite;
import com.example.BackendArchitectureLab.Entity.JobPosting;
import com.example.BackendArchitectureLab.Feign.AiServiceFeignClient;
import com.example.BackendArchitectureLab.Mapper.JobPostingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobPostingServiceTest {

    @Mock
    private IJobPostingDataAccess jobPostingDataAccess;

    @Mock
    private ICompanyDataAccess companyDataAccess;

    @Mock
    private JobPostingMapper jobPostingMapper;

    @Mock
    private CompositeJobCrawler jobCrawler;

    @Mock
    private AiServiceFeignClient aiServiceFeignClient;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private JobPostingService jobPostingService;

    private Company testCompany;
    private JobPosting testJobPosting;
    private JobPostingVo testJobPostingVo;
    private UUID companyId;
    private UUID jobPostingId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        jobPostingId = UUID.randomUUID();

        testCompany = new Company();
        testCompany.setId(companyId);
        testCompany.setName("Test Company");

        testJobPosting = new JobPosting();
        testJobPosting.setId(jobPostingId);
        testJobPosting.setCompany(testCompany);
        testJobPosting.setTitle("Software Engineer");
        testJobPosting.setUrl("https://example.com/job");

        testJobPostingVo = new JobPostingVo();
        testJobPostingVo.setId(jobPostingId.toString());
        testJobPostingVo.setCompanyId(companyId.toString());
        testJobPostingVo.setCompanyName("Test Company");
        testJobPostingVo.setTitle("Software Engineer");
        testJobPostingVo.setUrl("https://example.com/job");

        when(jobPostingMapper.toVo(any(JobPosting.class))).thenAnswer(invocation -> {
            JobPosting jp = invocation.getArgument(0);
            JobPostingVo vo = new JobPostingVo();
            if (jp.getId() != null) {
                vo.setId(jp.getId().toString());
            }
            if (jp.getCompany() != null) {
                vo.setCompanyId(jp.getCompany().getId().toString());
                vo.setCompanyName(jp.getCompany().getName());
            }
            vo.setTitle(jp.getTitle());
            vo.setUrl(jp.getUrl());
            vo.setDescription(jp.getDescription());
            vo.setRequirements(jp.getRequirements());
            vo.setResponsibilities(jp.getResponsibilities());
            vo.setSalaryRange(jp.getSalaryRange());
            vo.setPostedDate(jp.getPostedDate());
            vo.setGeminiAnalysis(jp.getGeminiAnalysis());
            return vo;
        });
    }

    @Test
    @DisplayName("Should create job posting successfully")
    void testCreateJobPosting() {
        CreateJobPostingRequest request = new CreateJobPostingRequest();
        request.setCompanyId(companyId.toString());
        request.setTitle("Software Engineer");
        request.setUrl("https://example.com/job");

        when(companyDataAccess.findById(companyId)).thenReturn(Optional.of(testCompany));
        when(jobPostingDataAccess.save(any(JobPosting.class))).thenAnswer(invocation -> {
            JobPosting jp = invocation.getArgument(0);
            jp.setId(jobPostingId);
            return jp;
        });

        JobPostingVo result = jobPostingService.createJobPosting(request);

        assertNotNull(result);
        assertEquals("Software Engineer", result.getTitle());
        verify(companyDataAccess).findById(companyId);
        verify(jobPostingDataAccess).save(any(JobPosting.class));
    }

    @Test
    @DisplayName("Should throw exception when company not found in createJobPosting")
    void testCreateJobPosting_CompanyNotFound() {
        CreateJobPostingRequest request = new CreateJobPostingRequest();
        request.setCompanyId(UUID.randomUUID().toString());
        request.setTitle("Title");
        request.setUrl("https://example.com");

        when(companyDataAccess.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> jobPostingService.createJobPosting(request));
    }

    @Test
    @DisplayName("Should get all job postings")
    void testGetAllJobPostings() {
        when(jobPostingDataAccess.findAll()).thenReturn(List.of(testJobPosting));

        List<JobPostingVo> result = jobPostingService.getAllJobPostings();

        assertEquals(1, result.size());
        assertEquals("Software Engineer", result.get(0).getTitle());
        verify(jobPostingDataAccess).findAll();
    }

    @Test
    @DisplayName("Should get job posting by id successfully")
    void testGetJobPostingById() {
        when(jobPostingDataAccess.findById(jobPostingId)).thenReturn(Optional.of(testJobPosting));

        JobPostingVo result = jobPostingService.getJobPostingById(jobPostingId.toString());

        assertNotNull(result);
        assertEquals(jobPostingId.toString(), result.getId());
        verify(jobPostingDataAccess).findById(jobPostingId);
    }

    @Test
    @DisplayName("Should throw exception when id is null in getJobPostingById")
    void testGetJobPostingById_NullId() {
        assertThrows(IllegalArgumentException.class, () -> jobPostingService.getJobPostingById(null));
    }

    @Test
    @DisplayName("Should throw exception when id is blank in getJobPostingById")
    void testGetJobPostingById_BlankId() {
        assertThrows(IllegalArgumentException.class, () -> jobPostingService.getJobPostingById("  "));
    }

    @Test
    @DisplayName("Should throw exception when job posting not found")
    void testGetJobPostingById_NotFound() {
        when(jobPostingDataAccess.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> jobPostingService.getJobPostingById(jobPostingId.toString()));
    }

    @Test
    @DisplayName("Should update job posting successfully")
    void testUpdateJobPosting() {
        JobPostingVo updateVo = new JobPostingVo();
        updateVo.setId(jobPostingId.toString());
        updateVo.setTitle("Senior Software Engineer");
        updateVo.setSalaryRange("100k-150k");

        when(jobPostingDataAccess.findById(jobPostingId)).thenReturn(Optional.of(testJobPosting));
        when(jobPostingDataAccess.save(any(JobPosting.class))).thenReturn(testJobPosting);

        JobPostingVo result = jobPostingService.updateJobPosting(updateVo);

        assertNotNull(result);
        verify(jobPostingDataAccess).findById(jobPostingId);
        verify(jobPostingDataAccess).save(any(JobPosting.class));
    }

    @Test
    @DisplayName("Should update job posting with company change")
    void testUpdateJobPosting_WithCompanyChange() {
        UUID newCompanyId = UUID.randomUUID();
        Company newCompany = new Company();
        newCompany.setId(newCompanyId);
        newCompany.setName("New Company");

        JobPostingVo updateVo = new JobPostingVo();
        updateVo.setId(jobPostingId.toString());
        updateVo.setCompanyId(newCompanyId.toString());

        when(jobPostingDataAccess.findById(jobPostingId)).thenReturn(Optional.of(testJobPosting));
        when(companyDataAccess.findById(newCompanyId)).thenReturn(Optional.of(newCompany));
        when(jobPostingDataAccess.save(any(JobPosting.class))).thenReturn(testJobPosting);

        JobPostingVo result = jobPostingService.updateJobPosting(updateVo);

        assertNotNull(result);
        verify(companyDataAccess).findById(newCompanyId);
    }

    @Test
    @DisplayName("Should throw exception when update id is null")
    void testUpdateJobPosting_NullId() {
        JobPostingVo updateVo = new JobPostingVo();

        assertThrows(IllegalArgumentException.class, () -> jobPostingService.updateJobPosting(updateVo));
    }

    @Test
    @DisplayName("Should throw exception when update id is blank")
    void testUpdateJobPosting_BlankId() {
        JobPostingVo updateVo = new JobPostingVo();
        updateVo.setId("  ");

        assertThrows(IllegalArgumentException.class, () -> jobPostingService.updateJobPosting(updateVo));
    }

    @Test
    @DisplayName("Should throw exception when job posting not found for update")
    void testUpdateJobPosting_NotFound() {
        JobPostingVo updateVo = new JobPostingVo();
        updateVo.setId(jobPostingId.toString());

        when(jobPostingDataAccess.findById(jobPostingId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> jobPostingService.updateJobPosting(updateVo));
    }

    @Test
    @DisplayName("Should throw exception when company not found for update")
    void testUpdateJobPosting_CompanyNotFound() {
        UUID newCompanyId = UUID.randomUUID();
        JobPostingVo updateVo = new JobPostingVo();
        updateVo.setId(jobPostingId.toString());
        updateVo.setCompanyId(newCompanyId.toString());

        when(jobPostingDataAccess.findById(jobPostingId)).thenReturn(Optional.of(testJobPosting));
        when(companyDataAccess.findById(newCompanyId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> jobPostingService.updateJobPosting(updateVo));
    }

    @Test
    @DisplayName("Should delete job posting successfully")
    void testDeleteJobPosting() {
        when(jobPostingDataAccess.findById(jobPostingId)).thenReturn(Optional.of(testJobPosting));

        jobPostingService.deleteJobPosting(jobPostingId.toString());

        verify(jobPostingDataAccess).findById(jobPostingId);
        verify(jobPostingDataAccess).deleteById(jobPostingId);
    }

    @Test
    @DisplayName("Should throw exception when delete id is null")
    void testDeleteJobPosting_NullId() {
        assertThrows(IllegalArgumentException.class, () -> jobPostingService.deleteJobPosting(null));
    }

    @Test
    @DisplayName("Should throw exception when job posting not found for delete")
    void testDeleteJobPosting_NotFound() {
        when(jobPostingDataAccess.findById(jobPostingId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> jobPostingService.deleteJobPosting(jobPostingId.toString()));
    }

    @Test
    @DisplayName("Should get job postings by company id")
    void testGetJobPostingsByCompanyId() {
        when(jobPostingDataAccess.findByCompanyId(companyId)).thenReturn(List.of(testJobPosting));

        List<JobPostingVo> result = jobPostingService.getJobPostingsByCompanyId(companyId.toString());

        assertEquals(1, result.size());
        verify(jobPostingDataAccess).findByCompanyId(companyId);
    }

    @Test
    @DisplayName("Should throw exception when company id is null")
    void testGetJobPostingsByCompanyId_NullId() {
        assertThrows(IllegalArgumentException.class,
                () -> jobPostingService.getJobPostingsByCompanyId(null));
    }

    @Test
    @DisplayName("Should update job posting with all fields set")
    void testUpdateJobPosting_AllFields() {
        JobPostingVo updateVo = new JobPostingVo();
        updateVo.setId(jobPostingId.toString());
        updateVo.setCompanyId(companyId.toString());
        updateVo.setTitle("Senior Software Engineer");
        updateVo.setUrl("https://example.com/new-job");
        updateVo.setDescription("New description");
        updateVo.setRequirements("New requirements");
        updateVo.setResponsibilities("New responsibilities");
        updateVo.setSalaryRange("150k-200k");
        updateVo.setPostedDate(LocalDate.now());
        updateVo.setGeminiAnalysis("New analysis");

        when(jobPostingDataAccess.findById(jobPostingId)).thenReturn(Optional.of(testJobPosting));
        when(companyDataAccess.findById(companyId)).thenReturn(Optional.of(testCompany));
        when(jobPostingDataAccess.save(any(JobPosting.class))).thenReturn(testJobPosting);

        JobPostingVo result = jobPostingService.updateJobPosting(updateVo);

        assertNotNull(result);
        verify(jobPostingDataAccess).findById(jobPostingId);
        verify(companyDataAccess).findById(companyId);
        verify(jobPostingDataAccess).save(any(JobPosting.class));
    }

    @Test
    @DisplayName("Should throw exception when company id is null in scrapeAndAnalyzeJobs")
    void testScrapeAndAnalyzeJobs_NullId() {
        assertThrows(IllegalArgumentException.class,
                () -> jobPostingService.scrapeAndAnalyzeJobs(null));
    }

    @Test
    @DisplayName("Should scrape and analyze jobs with existing match")
    void testScrapeAndAnalyzeJobs_WithExistingMatch() {
        String url = "https://example.com/careers";
        CompanyWebsite website = new CompanyWebsite(url);
        website.setCompany(testCompany);
        testCompany.setWebsites(List.of(website));

        JobPosting existingJob = new JobPosting();
        existingJob.setId(UUID.randomUUID());
        existingJob.setCompany(testCompany);
        existingJob.setTitle("Software Engineer");
        existingJob.setSalaryRange("100k-150k");

        AiJobPostingDto aiJob = AiJobPostingDto.builder()
                .title("Software Engineer")
                .salaryRange("100k-150k")
                .url("https://example.com/job")
                .build();

        when(companyDataAccess.findById(companyId)).thenReturn(Optional.of(testCompany));
        when(jobCrawler.crawl(url)).thenReturn("<html>careers page</html>");
        when(aiServiceFeignClient.analyzeJobPostings(anyString(), anyString()))
                .thenReturn(List.of(aiJob));
        when(jobPostingDataAccess.findByCompanyId(companyId)).thenReturn(List.of(existingJob));
        when(jobPostingDataAccess.save(any(JobPosting.class))).thenReturn(existingJob);

        List<JobPostingVo> result = jobPostingService.scrapeAndAnalyzeJobs(companyId.toString());

        assertEquals(1, result.size());
        verify(jobPostingDataAccess).save(any(JobPosting.class));
    }

    @Test
    @DisplayName("Should scrape and analyze jobs with null title in AI data")
    void testScrapeAndAnalyzeJobs_NullTitle() {
        String url = "https://example.com/careers";
        CompanyWebsite website = new CompanyWebsite(url);
        website.setCompany(testCompany);
        testCompany.setWebsites(List.of(website));

        AiJobPostingDto aiJob = AiJobPostingDto.builder()
                .title(null)
                .url("https://example.com/job")
                .build();

        when(companyDataAccess.findById(companyId)).thenReturn(Optional.of(testCompany));
        when(jobCrawler.crawl(url)).thenReturn("<html>job content</html>");
        when(aiServiceFeignClient.analyzeJobPostings(anyString(), anyString()))
                .thenReturn(List.of(aiJob));
        when(jobPostingDataAccess.findByCompanyId(companyId)).thenReturn(List.of());
        when(jobPostingDataAccess.save(any(JobPosting.class))).thenAnswer(invocation -> {
            JobPosting jp = invocation.getArgument(0);
            jp.setId(UUID.randomUUID());
            return jp;
        });

        List<JobPostingVo> result = jobPostingService.scrapeAndAnalyzeJobs(companyId.toString());

        assertEquals(1, result.size());
        assertEquals("Unknown Title", result.get(0).getTitle());
    }

    @Test
    @DisplayName("Should search job postings successfully")
    void testSearchJobPostings() {
        JobPostingSearchQuery query = new JobPostingSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("title");
        query.setSortDir("asc");

        Page<JobPosting> page = new PageImpl<>(
                List.of(testJobPosting), PageRequest.of(0, 20), 1);

        when(jobPostingDataAccess.searchJobPostings(any(JobPostingSearchQuery.class))).thenReturn(page);

        PageResult<JobPostingVo> result = jobPostingService.searchJobPostings(query);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
        verify(jobPostingDataAccess).searchJobPostings(any(JobPostingSearchQuery.class));
    }

    @Test
    @DisplayName("Should scrape and analyze jobs successfully")
    void testScrapeAndAnalyzeJobs() {
        String url = "https://example.com/careers";
        CompanyWebsite website = new CompanyWebsite(url);
        website.setCompany(testCompany);
        testCompany.setWebsites(List.of(website));

        when(companyDataAccess.findById(companyId)).thenReturn(Optional.of(testCompany));
        when(jobCrawler.crawl(url)).thenReturn("<html>job content</html>");

        AiJobPostingDto aiJob = AiJobPostingDto.builder()
                .title("Software Engineer")
                .url("https://example.com/job")
                .build();
        when(aiServiceFeignClient.analyzeJobPostings(anyString(), anyString()))
                .thenReturn(List.of(aiJob));
        when(jobPostingDataAccess.findByCompanyId(companyId)).thenReturn(List.of());
        when(jobPostingDataAccess.save(any(JobPosting.class))).thenAnswer(invocation -> {
            JobPosting jp = invocation.getArgument(0);
            jp.setId(UUID.randomUUID());
            return jp;
        });

        List<JobPostingVo> result = jobPostingService.scrapeAndAnalyzeJobs(companyId.toString());

        assertFalse(result.isEmpty());
        verify(jobCrawler).crawl(url);
        verify(aiServiceFeignClient).analyzeJobPostings(anyString(), anyString());
        verify(companyDataAccess).save(testCompany);
    }

    @Test
    @DisplayName("Should return empty list when company has no websites")
    void testScrapeAndAnalyzeJobs_NoWebsites() {
        testCompany.setWebsites(List.of());

        when(companyDataAccess.findById(companyId)).thenReturn(Optional.of(testCompany));

        List<JobPostingVo> result = jobPostingService.scrapeAndAnalyzeJobs(companyId.toString());

        assertTrue(result.isEmpty());
        verify(companyDataAccess).findById(companyId);
        verifyNoInteractions(jobCrawler);
    }

    @Test
    @DisplayName("Should handle crawl failure gracefully")
    void testScrapeAndAnalyzeJobs_CrawlFailure() {
        String url = "https://example.com/careers";
        CompanyWebsite website = new CompanyWebsite(url);
        website.setCompany(testCompany);
        testCompany.setWebsites(List.of(website));

        when(companyDataAccess.findById(companyId)).thenReturn(Optional.of(testCompany));
        when(jobCrawler.crawl(url)).thenThrow(new RuntimeException("Crawl failed"));
        when(jobPostingDataAccess.findByCompanyId(companyId)).thenReturn(List.of());

        List<JobPostingVo> result = jobPostingService.scrapeAndAnalyzeJobs(companyId.toString());

        assertTrue(result.isEmpty());
        verify(jobCrawler).crawl(url);
    }

    @Test
    @DisplayName("Should scrape and analyze all companies")
    void testScrapeAndAnalyzeAllCompanies() {
        Company company1 = new Company();
        company1.setId(UUID.randomUUID());
        company1.setName("Company 1");
        company1.setLastScrapedAt(LocalDate.now().minusDays(1));

        Company company2 = new Company();
        company2.setId(UUID.randomUUID());
        company2.setName("Company 2");
        company2.setLastScrapedAt(LocalDate.now().minusDays(2));

        when(companyDataAccess.findAll()).thenReturn(List.of(company1, company2));

        jobPostingService.scrapeAndAnalyzeAllCompanies();

        verify(companyDataAccess, times(2)).findById(any(UUID.class));
    }

    @Test
    @DisplayName("Should skip companies already scraped today")
    void testScrapeAndAnalyzeAllCompanies_SkipToday() {
        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Already Scraped");
        company.setLastScrapedAt(LocalDate.now());

        when(companyDataAccess.findAll()).thenReturn(List.of(company));

        jobPostingService.scrapeAndAnalyzeAllCompanies();

        verify(companyDataAccess, never()).findById(any(UUID.class));
    }
}
