package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.DataAccess.ICompanyDataAccess;
import com.example.BackendArchitectureLab.DataAccess.impl.CompanyDataAccessImpl;
import com.example.BackendArchitectureLab.Entity.Company;
import com.example.BackendArchitectureLab.Repository.CompanyRepository;
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
@Import(CompanyDataAccessImpl.class)
class CompanyDataAccessImplTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ICompanyDataAccess companyDataAccess;

    @BeforeEach
    void setUp() {
        companyRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save company successfully")
    void testSave() {
        Company company = new Company();
        company.setName("Test Company");

        companyDataAccess.save(company);

        List<Company> result = companyRepository.findAll();
        assertEquals(1, result.size());
        assertEquals("Test Company", result.get(0).getName());
    }

    @Test
    @DisplayName("Should find all companies")
    void testFindAll() {
        Company company1 = new Company();
        company1.setName("Company A");
        Company company2 = new Company();
        company2.setName("Company B");
        companyRepository.save(company1);
        companyRepository.save(company2);

        List<Company> result = companyDataAccess.findAll();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should find company by id")
    void testFindById() {
        Company company = new Company();
        company.setName("Find Me");
        Company saved = companyRepository.save(company);

        Optional<Company> result = companyDataAccess.findById(saved.getId());

        assertTrue(result.isPresent());
        assertEquals("Find Me", result.get().getName());
    }

    @Test
    @DisplayName("Should return empty when company not found by id")
    void testFindById_NotFound() {
        Optional<Company> result = companyDataAccess.findById(UUID.randomUUID());

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should check if company exists by id")
    void testExistsById() {
        Company company = new Company();
        company.setName("Exists");
        Company saved = companyRepository.save(company);

        assertTrue(companyDataAccess.existsById(saved.getId()));
        assertFalse(companyDataAccess.existsById(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Should delete company by id")
    void testDeleteById() {
        Company company = new Company();
        company.setName("Delete Me");
        Company saved = companyRepository.save(company);

        companyDataAccess.deleteById(saved.getId());

        assertEquals(0, companyRepository.findAll().size());
    }
}
