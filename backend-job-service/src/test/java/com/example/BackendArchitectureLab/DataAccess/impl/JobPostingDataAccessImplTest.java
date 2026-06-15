package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.DataAccess.IJobPostingDataAccess;
import com.example.BackendArchitectureLab.DataAccess.impl.JobPostingDataAccessImpl;
import com.example.BackendArchitectureLab.Dto.Vo.Search.JobPostingSearchQuery;
import com.example.BackendArchitectureLab.Entity.Company;
import com.example.BackendArchitectureLab.Entity.JobPosting;
import com.example.BackendArchitectureLab.Repository.CompanyRepository;
import com.example.BackendArchitectureLab.Repository.JobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JobPostingDataAccessImpl.class)
class JobPostingDataAccessImplTest {

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private IJobPostingDataAccess jobPostingDataAccess;
    private Company testCompany;

    @BeforeEach
    void setUp() {
        jobPostingRepository.deleteAll();
        companyRepository.deleteAll();

        testCompany = new Company();
        testCompany.setName("Test Company");
        testCompany = companyRepository.save(testCompany);
    }

    @Test
    @DisplayName("Should save job posting successfully")
    void testSave() {
        JobPosting jobPosting = new JobPosting();
        jobPosting.setCompany(testCompany);
        jobPosting.setTitle("Software Engineer");
        jobPosting.setUrl("https://example.com/job");

        jobPostingDataAccess.save(jobPosting);

        List<JobPosting> result = jobPostingRepository.findAll();
        assertEquals(1, result.size());
        assertEquals("Software Engineer", result.get(0).getTitle());
    }

    @Test
    @DisplayName("Should find all job postings")
    void testFindAll() {
        JobPosting jp1 = new JobPosting();
        jp1.setCompany(testCompany);
        jp1.setTitle("Job 1");
        jp1.setUrl("https://example.com/1");
        JobPosting jp2 = new JobPosting();
        jp2.setCompany(testCompany);
        jp2.setTitle("Job 2");
        jp2.setUrl("https://example.com/2");
        jobPostingRepository.save(jp1);
        jobPostingRepository.save(jp2);

        List<JobPosting> result = jobPostingDataAccess.findAll();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should find job posting by id")
    void testFindById() {
        JobPosting jobPosting = new JobPosting();
        jobPosting.setCompany(testCompany);
        jobPosting.setTitle("Find Me");
        jobPosting.setUrl("https://example.com/find");
        JobPosting saved = jobPostingRepository.save(jobPosting);

        Optional<JobPosting> result = jobPostingDataAccess.findById(saved.getId());

        assertTrue(result.isPresent());
        assertEquals("Find Me", result.get().getTitle());
    }

    @Test
    @DisplayName("Should return empty when job posting not found by id")
    void testFindById_NotFound() {
        Optional<JobPosting> result = jobPostingDataAccess.findById(UUID.randomUUID());

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should check if job posting exists by id")
    void testExistsById() {
        JobPosting jobPosting = new JobPosting();
        jobPosting.setCompany(testCompany);
        jobPosting.setTitle("Exists");
        jobPosting.setUrl("https://example.com/exists");
        JobPosting saved = jobPostingRepository.save(jobPosting);

        assertTrue(jobPostingDataAccess.existsById(saved.getId()));
        assertFalse(jobPostingDataAccess.existsById(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Should delete job posting by id")
    void testDeleteById() {
        JobPosting jobPosting = new JobPosting();
        jobPosting.setCompany(testCompany);
        jobPosting.setTitle("Delete Me");
        jobPosting.setUrl("https://example.com/delete");
        JobPosting saved = jobPostingRepository.save(jobPosting);

        jobPostingDataAccess.deleteById(saved.getId());

        assertEquals(0, jobPostingRepository.findAll().size());
    }

    @Test
    @DisplayName("Should find job postings by company id")
    void testFindByCompanyId() {
        JobPosting jp1 = new JobPosting();
        jp1.setCompany(testCompany);
        jp1.setTitle("Job 1");
        jp1.setUrl("https://example.com/1");
        jobPostingRepository.save(jp1);

        Company otherCompany = new Company();
        otherCompany.setName("Other Company");
        otherCompany = companyRepository.save(otherCompany);
        JobPosting jp2 = new JobPosting();
        jp2.setCompany(otherCompany);
        jp2.setTitle("Other Job");
        jp2.setUrl("https://other.com/job");
        jobPostingRepository.save(jp2);

        List<JobPosting> result = jobPostingDataAccess.findByCompanyId(testCompany.getId());

        assertEquals(1, result.size());
        assertEquals("Job 1", result.get(0).getTitle());
    }

    @Test
    @DisplayName("Should search job postings by title")
    void testSearchJobPostings_ByTitle() {
        JobPosting jp1 = new JobPosting();
        jp1.setCompany(testCompany);
        jp1.setTitle("Software Engineer");
        jp1.setUrl("https://example.com/se");
        jobPostingRepository.save(jp1);
        JobPosting jp2 = new JobPosting();
        jp2.setCompany(testCompany);
        jp2.setTitle("DevOps Engineer");
        jp2.setUrl("https://example.com/devops");
        jobPostingRepository.save(jp2);

        JobPostingSearchQuery query = new JobPostingSearchQuery();
        query.setTitle("Software");
        query.setPage(0);
        query.setSize(10);

        Page<JobPosting> result = jobPostingDataAccess.searchJobPostings(query);

        assertEquals(1, result.getTotalElements());
        assertEquals("Software Engineer", result.getContent().get(0).getTitle());
    }

    @Test
    @DisplayName("Should search job postings with pagination")
    void testSearchJobPostings_Pagination() {
        for (int i = 0; i < 5; i++) {
            JobPosting jp = new JobPosting();
            jp.setCompany(testCompany);
            jp.setTitle("Job " + i);
            jp.setUrl("https://example.com/job" + i);
            jobPostingRepository.save(jp);
        }

        JobPostingSearchQuery query = new JobPostingSearchQuery();
        query.setPage(0);
        query.setSize(2);

        Page<JobPosting> result = jobPostingDataAccess.searchJobPostings(query);

        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertEquals(2, result.getContent().size());
    }
}
