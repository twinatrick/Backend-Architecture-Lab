package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.ICompanyWebsiteDataAccess;
import com.example.BackendArchitectureLab.Entity.CompanyWebsite;
import com.example.BackendArchitectureLab.Repository.CompanyWebsiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CompanyWebsiteDataAccessImpl implements ICompanyWebsiteDataAccess {

    private final CompanyWebsiteRepository companyWebsiteRepository;

    @Override
    public CompanyWebsite save(CompanyWebsite companyWebsite) {
        return companyWebsiteRepository.save(companyWebsite);
    }

    @Override
    public List<CompanyWebsite> findAll() {
        return companyWebsiteRepository.findAll();
    }

    @Override
    public Optional<CompanyWebsite> findById(UUID id) {
        return companyWebsiteRepository.findById(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return companyWebsiteRepository.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        companyWebsiteRepository.deleteById(id);
    }

    @Override
    public List<CompanyWebsite> findByCompanyId(UUID companyId) {
        return companyWebsiteRepository.findByCompanyId(companyId);
    }
}
