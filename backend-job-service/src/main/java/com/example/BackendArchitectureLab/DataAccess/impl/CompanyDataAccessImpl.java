package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.DataAccess.ICompanyDataAccess;
import com.example.BackendArchitectureLab.Entity.Company;
import com.example.BackendArchitectureLab.Repository.CompanyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of ICompanyDataAccess.
 * Delegates to Spring Data JPA CompanyRepository.
 */
@Component
public class CompanyDataAccessImpl implements ICompanyDataAccess {

    @Autowired
    private CompanyRepository companyRepository;

    @Override
    public Company save(Company company) {
        return companyRepository.save(company);
    }

    @Override
    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    @Override
    public Optional<Company> findById(UUID id) {
        return companyRepository.findById(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return companyRepository.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        companyRepository.deleteById(id);
    }
}
