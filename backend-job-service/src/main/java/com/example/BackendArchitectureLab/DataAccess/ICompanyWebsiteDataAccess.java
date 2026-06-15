package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.CompanyWebsite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ICompanyWebsiteDataAccess {

    CompanyWebsite save(CompanyWebsite companyWebsite);

    List<CompanyWebsite> findAll();

    Optional<CompanyWebsite> findById(UUID id);

    boolean existsById(UUID id);

    void deleteById(UUID id);

    List<CompanyWebsite> findByCompanyId(UUID companyId);
}
