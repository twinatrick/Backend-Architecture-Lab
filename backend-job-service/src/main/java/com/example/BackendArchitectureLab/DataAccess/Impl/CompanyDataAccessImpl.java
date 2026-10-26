package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.ICompanyDataAccess;
import com.example.BackendArchitectureLab.DataAccess.specification.CompanySpecification;
import com.example.BackendArchitectureLab.Dto.Query.CompanySearchQuery;
import com.example.BackendArchitectureLab.Entity.Company;
import com.example.BackendArchitectureLab.Repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of ICompanyDataAccess.
 * Delegates to Spring Data JPA CompanyRepository.
 */
@Component
@RequiredArgsConstructor
public class CompanyDataAccessImpl implements ICompanyDataAccess {

    private final CompanyRepository companyRepository;

    @Override
    public Company save(Company company) {
        return companyRepository.save(company);
    }

    @Override
    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    @Override
    public Page<Company> findAllPaged(Pageable pageable) {
        return companyRepository.findAll(pageable);
    }

    @Override
    public Optional<Company> findById(UUID id) {
        return companyRepository.findById(id);
    }

    @Override
    public List<Company> findAllById(List<UUID> ids) {
        return companyRepository.findAllById(ids);
    }

    @Override
    public boolean existsById(UUID id) {
        return companyRepository.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        companyRepository.deleteById(id);
    }

    @Override
    public Page<Company> searchCompanies(CompanySearchQuery query) {
        Sort sort = Sort.by(
            "asc".equalsIgnoreCase(query.getNormalizedSortDir())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC,
            query.getSortBy()
        );
        PageRequest pageRequest = PageRequest.of(query.getPage(), query.getSize(), sort);
        Page<Company> page = companyRepository.findAll(CompanySpecification.buildSpecification(query), pageRequest);
        if (page.isEmpty()) {
            return page;
        }
        List<UUID> ids = page.getContent().stream().map(Company::getId).toList();
        List<Company> enriched = companyRepository.findAllByIdsWithWebsites(ids);
        Map<UUID, Company> enrichedMap = enriched.stream()
                .collect(Collectors.toMap(Company::getId, c -> c, (existing, replacement) -> existing));
        List<Company> ordered = page.getContent().stream()
                .map(c -> enrichedMap.getOrDefault(c.getId(), c))
                .toList();
        return new PageImpl<>(ordered, pageRequest, page.getTotalElements());
    }
}
