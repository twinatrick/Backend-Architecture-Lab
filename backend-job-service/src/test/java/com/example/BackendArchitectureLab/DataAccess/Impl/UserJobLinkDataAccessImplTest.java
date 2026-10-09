package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IUserJobLinkDataAccess;
import com.example.BackendArchitectureLab.DataAccess.Impl.UserJobLinkDataAccessImpl;
import com.example.BackendArchitectureLab.Entity.Company;
import com.example.BackendArchitectureLab.Entity.JobPosting;
import com.example.BackendArchitectureLab.Entity.UserJobLink;
import com.example.BackendArchitectureLab.Repository.CompanyRepository;
import com.example.BackendArchitectureLab.Repository.JobPostingRepository;
import com.example.BackendArchitectureLab.Repository.UserJobLinkRepository;
import com.example.BackendArchitectureLab.Vo.Search.UserJobLinkSearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserJobLinkDataAccessImplTest {

    private final UserJobLinkRepository userJobLinkRepository;
    private final JobPostingRepository jobPostingRepository;
    private final CompanyRepository companyRepository;
    private final IUserJobLinkDataAccess userJobLinkDataAccess;
    private UUID testUserId;
    private JobPosting testJobPosting;

    @Autowired
    public UserJobLinkDataAccessImplTest(UserJobLinkRepository userJobLinkRepository,
                                        JobPostingRepository jobPostingRepository,
                                        CompanyRepository companyRepository) {
        this.userJobLinkRepository = userJobLinkRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.companyRepository = companyRepository;
        this.userJobLinkDataAccess = new UserJobLinkDataAccessImpl(userJobLinkRepository);
    }

    @BeforeEach
    void setUp() {
        userJobLinkRepository.deleteAll();
        jobPostingRepository.deleteAll();
        companyRepository.deleteAll();
        testUserId = UUID.randomUUID();

        Company company = new Company();
        company.setName("Test Company");
        company = companyRepository.save(company);

        testJobPosting = new JobPosting();
        testJobPosting.setCompany(company);
        testJobPosting.setTitle("Software Engineer");
        testJobPosting.setUrl("https://example.com/job");
        testJobPosting = jobPostingRepository.save(testJobPosting);
    }

    @Test
    @DisplayName("Should save user job link successfully")
    void testSave() {
        UserJobLink link = new UserJobLink();
        link.setUserId(testUserId);
        link.setJobPosting(testJobPosting);
        link.setUserNotes("Interested");

        userJobLinkDataAccess.save(link);

        List<UserJobLink> result = userJobLinkRepository.findAll();
        assertEquals(1, result.size());
        assertEquals("Interested", result.get(0).getUserNotes());
    }

    @Test
    @DisplayName("Should find all user job links")
    void testFindAll() {
        UserJobLink link1 = new UserJobLink();
        link1.setUserId(testUserId);
        link1.setJobPosting(testJobPosting);
        userJobLinkRepository.save(link1);

        UserJobLink link2 = new UserJobLink();
        link2.setUserId(UUID.randomUUID());
        link2.setJobPosting(testJobPosting);
        userJobLinkRepository.save(link2);

        List<UserJobLink> result = userJobLinkDataAccess.findAll();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should find user job link by id")
    void testFindById() {
        UserJobLink link = new UserJobLink();
        link.setUserId(testUserId);
        link.setJobPosting(testJobPosting);
        UserJobLink saved = userJobLinkRepository.save(link);

        Optional<UserJobLink> result = userJobLinkDataAccess.findById(saved.getId());

        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Should return empty when link not found by id")
    void testFindById_NotFound() {
        Optional<UserJobLink> result = userJobLinkDataAccess.findById(UUID.randomUUID());

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should check if user job link exists by id")
    void testExistsById() {
        UserJobLink link = new UserJobLink();
        link.setUserId(testUserId);
        link.setJobPosting(testJobPosting);
        UserJobLink saved = userJobLinkRepository.save(link);

        assertTrue(userJobLinkDataAccess.existsById(saved.getId()));
        assertFalse(userJobLinkDataAccess.existsById(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Should delete user job link by id")
    void testDeleteById() {
        UserJobLink link = new UserJobLink();
        link.setUserId(testUserId);
        link.setJobPosting(testJobPosting);
        UserJobLink saved = userJobLinkRepository.save(link);

        userJobLinkDataAccess.deleteById(saved.getId());

        assertEquals(0, userJobLinkRepository.findAll().size());
    }

    @Test
    @DisplayName("Should find links by user id")
    void testFindByUserId() {
        UserJobLink link = new UserJobLink();
        link.setUserId(testUserId);
        link.setJobPosting(testJobPosting);
        userJobLinkRepository.save(link);

        List<UserJobLink> result = userJobLinkDataAccess.findByUserId(testUserId);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should return empty list when no links for user")
    void testFindByUserId_NotFound() {
        List<UserJobLink> result = userJobLinkDataAccess.findByUserId(UUID.randomUUID());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should find links by job posting id")
    void testFindByJobPostingId() {
        UserJobLink link = new UserJobLink();
        link.setUserId(testUserId);
        link.setJobPosting(testJobPosting);
        userJobLinkRepository.save(link);

        List<UserJobLink> result = userJobLinkDataAccess.findByJobPostingId(testJobPosting.getId());

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should find link by user id and job posting id")
    void testFindByUserIdAndJobPostingId() {
        UserJobLink link = new UserJobLink();
        link.setUserId(testUserId);
        link.setJobPosting(testJobPosting);
        userJobLinkRepository.save(link);

        Optional<UserJobLink> result = userJobLinkDataAccess.findByUserIdAndJobPostingId(
                testUserId, testJobPosting.getId());

        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Should delete link by user id and job posting id")
    void testDeleteByUserIdAndJobPostingId() {
        UserJobLink link = new UserJobLink();
        link.setUserId(testUserId);
        link.setJobPosting(testJobPosting);
        userJobLinkRepository.save(link);

        userJobLinkDataAccess.deleteByUserIdAndJobPostingId(testUserId, testJobPosting.getId());

        assertEquals(0, userJobLinkRepository.findAll().size());
    }

    @Test
    @DisplayName("Should check existence by user id and job posting id")
    void testExistsByUserIdAndJobPostingId() {
        UserJobLink link = new UserJobLink();
        link.setUserId(testUserId);
        link.setJobPosting(testJobPosting);
        userJobLinkRepository.save(link);

        assertTrue(userJobLinkDataAccess.existsByUserIdAndJobPostingId(
                testUserId, testJobPosting.getId()));
        assertFalse(userJobLinkDataAccess.existsByUserIdAndJobPostingId(
                UUID.randomUUID(), testJobPosting.getId()));
    }

    @Test
    @DisplayName("Should search user job links with all filters")
    void testSearchUserJobLinks_allFilters() {
        UserJobLink link = new UserJobLink();
        link.setUserId(testUserId);
        link.setJobPosting(testJobPosting);
        link.setUserNotes("Interested");
        link.setCreatedBy("admin");
        userJobLinkRepository.save(link);

        UserJobLinkSearchQuery query = new UserJobLinkSearchQuery();
        query.setJobTitle("Software");
        query.setCompanyName("Test Company");
        query.setUserId(testUserId.toString());
        query.setJobPostingId(testJobPosting.getId().toString());
        query.setCreatedBy("admin");

        Page<UserJobLink> result = userJobLinkDataAccess.searchUserJobLinks(query);

        assertEquals(1, result.getTotalElements());
        assertEquals(testUserId, result.getContent().get(0).getUserId());
    }

    @Test
    @DisplayName("Should search user job links with pagination and sorting")
    void testSearchUserJobLinks_paginationAndSorting() {
        for (int i = 0; i < 5; i++) {
            UserJobLink link = new UserJobLink();
            link.setUserId(UUID.randomUUID());
            link.setJobPosting(testJobPosting);
            link.setUserNotes("Note " + i);
            userJobLinkRepository.save(link);
        }

        UserJobLinkSearchQuery query = new UserJobLinkSearchQuery();
        query.setPage(0);
        query.setSize(2);
        query.setSortBy("userNotes");
        query.setSortDir("asc");

        Page<UserJobLink> result = userJobLinkDataAccess.searchUserJobLinks(query);

        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertEquals(2, result.getContent().size());
        assertEquals("Note 0", result.getContent().get(0).getUserNotes());
    }

    @Test
    @DisplayName("Should return empty page when no matching records")
    void testSearchUserJobLinks_emptyResult() {
        UserJobLinkSearchQuery query = new UserJobLinkSearchQuery();
        query.setJobTitle("NonExistentJobTitle");

        Page<UserJobLink> result = userJobLinkDataAccess.searchUserJobLinks(query);

        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }
}
