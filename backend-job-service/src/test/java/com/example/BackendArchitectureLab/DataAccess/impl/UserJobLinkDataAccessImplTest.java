package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.DataAccess.IUserJobLinkDataAccess;
import com.example.BackendArchitectureLab.DataAccess.impl.UserJobLinkDataAccessImpl;
import com.example.BackendArchitectureLab.Entity.Company;
import com.example.BackendArchitectureLab.Entity.JobPosting;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserJobLink;
import com.example.BackendArchitectureLab.Repository.CompanyRepository;
import com.example.BackendArchitectureLab.Repository.JobPostingRepository;
import com.example.BackendArchitectureLab.Repository.UserJobLinkRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(UserJobLinkDataAccessImpl.class)
class UserJobLinkDataAccessImplTest {

    @Autowired
    private UserJobLinkRepository userJobLinkRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private IUserJobLinkDataAccess userJobLinkDataAccess;
    private User testUser;
    private JobPosting testJobPosting;

    @BeforeEach
    void setUp() {
        userJobLinkRepository.deleteAll();
        jobPostingRepository.deleteAll();
        companyRepository.deleteAll();
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setPassword("password");
        entityManager.persist(testUser);
        entityManager.flush();

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
        link.setUser(testUser);
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
        link1.setUser(testUser);
        link1.setJobPosting(testJobPosting);
        userJobLinkRepository.save(link1);

        UserJobLink link2 = new UserJobLink();
        link2.setUser(testUser);
        link2.setJobPosting(testJobPosting);
        userJobLinkRepository.save(link2);

        List<UserJobLink> result = userJobLinkDataAccess.findAll();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should find user job link by id")
    void testFindById() {
        UserJobLink link = new UserJobLink();
        link.setUser(testUser);
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
        link.setUser(testUser);
        link.setJobPosting(testJobPosting);
        UserJobLink saved = userJobLinkRepository.save(link);

        assertTrue(userJobLinkDataAccess.existsById(saved.getId()));
        assertFalse(userJobLinkDataAccess.existsById(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Should delete user job link by id")
    void testDeleteById() {
        UserJobLink link = new UserJobLink();
        link.setUser(testUser);
        link.setJobPosting(testJobPosting);
        UserJobLink saved = userJobLinkRepository.save(link);

        userJobLinkDataAccess.deleteById(saved.getId());

        assertEquals(0, userJobLinkRepository.findAll().size());
    }

    @Test
    @DisplayName("Should find links by user id")
    void testFindByUserId() {
        UserJobLink link = new UserJobLink();
        link.setUser(testUser);
        link.setJobPosting(testJobPosting);
        userJobLinkRepository.save(link);

        List<UserJobLink> result = userJobLinkDataAccess.findByUserId(testUser.getId());

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
        link.setUser(testUser);
        link.setJobPosting(testJobPosting);
        userJobLinkRepository.save(link);

        List<UserJobLink> result = userJobLinkDataAccess.findByJobPostingId(testJobPosting.getId());

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should find link by user id and job posting id")
    void testFindByUserIdAndJobPostingId() {
        UserJobLink link = new UserJobLink();
        link.setUser(testUser);
        link.setJobPosting(testJobPosting);
        userJobLinkRepository.save(link);

        Optional<UserJobLink> result = userJobLinkDataAccess.findByUserIdAndJobPostingId(
                testUser.getId(), testJobPosting.getId());

        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Should delete link by user id and job posting id")
    void testDeleteByUserIdAndJobPostingId() {
        UserJobLink link = new UserJobLink();
        link.setUser(testUser);
        link.setJobPosting(testJobPosting);
        userJobLinkRepository.save(link);

        userJobLinkDataAccess.deleteByUserIdAndJobPostingId(testUser.getId(), testJobPosting.getId());

        assertEquals(0, userJobLinkRepository.findAll().size());
    }

    @Test
    @DisplayName("Should check existence by user id and job posting id")
    void testExistsByUserIdAndJobPostingId() {
        UserJobLink link = new UserJobLink();
        link.setUser(testUser);
        link.setJobPosting(testJobPosting);
        userJobLinkRepository.save(link);

        assertTrue(userJobLinkDataAccess.existsByUserIdAndJobPostingId(
                testUser.getId(), testJobPosting.getId()));
        assertFalse(userJobLinkDataAccess.existsByUserIdAndJobPostingId(
                UUID.randomUUID(), testJobPosting.getId()));
    }
}
