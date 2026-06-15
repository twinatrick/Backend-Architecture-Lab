package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.DataAccess.ICompanyWebsiteDataAccess;
import com.example.BackendArchitectureLab.DataAccess.impl.CompanyWebsiteDataAccessImpl;
import com.example.BackendArchitectureLab.Entity.Company;
import com.example.BackendArchitectureLab.Entity.CompanyWebsite;
import com.example.BackendArchitectureLab.Repository.CompanyRepository;
import com.example.BackendArchitectureLab.Repository.CompanyWebsiteRepository;
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
@Import(CompanyWebsiteDataAccessImpl.class)
class CompanyWebsiteDataAccessImplTest {

    @Autowired
    private CompanyWebsiteRepository companyWebsiteRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ICompanyWebsiteDataAccess companyWebsiteDataAccess;
    private Company testCompany;

    @BeforeEach
    void setUp() {
        companyWebsiteRepository.deleteAll();
        companyRepository.deleteAll();

        testCompany = new Company();
        testCompany.setName("Test Company");
        testCompany = companyRepository.save(testCompany);
    }

    @Test
    @DisplayName("Should save company website successfully")
    void testSave() {
        CompanyWebsite website = new CompanyWebsite("https://example.com");
        website.setCompany(testCompany);

        companyWebsiteDataAccess.save(website);

        List<CompanyWebsite> result = companyWebsiteRepository.findAll();
        assertEquals(1, result.size());
        assertEquals("https://example.com", result.get(0).getUrl());
    }

    @Test
    @DisplayName("Should find all company websites")
    void testFindAll() {
        CompanyWebsite w1 = new CompanyWebsite("https://example1.com");
        w1.setCompany(testCompany);
        CompanyWebsite w2 = new CompanyWebsite("https://example2.com");
        w2.setCompany(testCompany);
        companyWebsiteRepository.save(w1);
        companyWebsiteRepository.save(w2);

        List<CompanyWebsite> result = companyWebsiteDataAccess.findAll();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should find company website by id")
    void testFindById() {
        CompanyWebsite website = new CompanyWebsite("https://find.com");
        website.setCompany(testCompany);
        CompanyWebsite saved = companyWebsiteRepository.save(website);

        Optional<CompanyWebsite> result = companyWebsiteDataAccess.findById(saved.getId());

        assertTrue(result.isPresent());
        assertEquals("https://find.com", result.get().getUrl());
    }

    @Test
    @DisplayName("Should return empty when website not found by id")
    void testFindById_NotFound() {
        Optional<CompanyWebsite> result = companyWebsiteDataAccess.findById(UUID.randomUUID());

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should check if company website exists by id")
    void testExistsById() {
        CompanyWebsite website = new CompanyWebsite("https://exists.com");
        website.setCompany(testCompany);
        CompanyWebsite saved = companyWebsiteRepository.save(website);

        assertTrue(companyWebsiteDataAccess.existsById(saved.getId()));
        assertFalse(companyWebsiteDataAccess.existsById(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Should delete company website by id")
    void testDeleteById() {
        CompanyWebsite website = new CompanyWebsite("https://delete.com");
        website.setCompany(testCompany);
        CompanyWebsite saved = companyWebsiteRepository.save(website);

        companyWebsiteDataAccess.deleteById(saved.getId());

        assertEquals(0, companyWebsiteRepository.findAll().size());
    }

    @Test
    @DisplayName("Should find websites by company id")
    void testFindByCompanyId() {
        CompanyWebsite w1 = new CompanyWebsite("https://example1.com");
        w1.setCompany(testCompany);
        CompanyWebsite w2 = new CompanyWebsite("https://example2.com");
        w2.setCompany(testCompany);
        companyWebsiteRepository.save(w1);
        companyWebsiteRepository.save(w2);

        Company otherCompany = new Company();
        otherCompany.setName("Other Company");
        otherCompany = companyRepository.save(otherCompany);
        CompanyWebsite w3 = new CompanyWebsite("https://other.com");
        w3.setCompany(otherCompany);
        companyWebsiteRepository.save(w3);

        List<CompanyWebsite> result = companyWebsiteDataAccess.findByCompanyId(testCompany.getId());

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should return empty list when no websites for company")
    void testFindByCompanyId_NotFound() {
        List<CompanyWebsite> result = companyWebsiteDataAccess.findByCompanyId(UUID.randomUUID());

        assertTrue(result.isEmpty());
    }
}
