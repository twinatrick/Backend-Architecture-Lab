package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.DataAccess.ICompanyDataAccess;
import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.CompanyVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.CreateCompanyRequest;
import com.example.BackendArchitectureLab.Vo.Search.CompanySearchQuery;
import com.example.BackendArchitectureLab.Vo.UpdateCompanyRequest;
import com.example.BackendArchitectureLab.Entity.Company;
import com.example.BackendArchitectureLab.Mapper.CompanyMapper;
import com.example.BackendArchitectureLab.Service.ICompanyService;
import com.example.BackendArchitectureLab.Util.SortFieldValidator;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CompanyService implements ICompanyService {

    @Autowired
    private TransactionExecutor transactionExecutor;

    @Autowired
    private ICompanyDataAccess companyDataAccess;
    @Autowired
    private CompanyMapper companyMapper;
    @Autowired
    private CacheManager cacheManager;

    @Override
    @Transactional
    @CacheEvict(value = "companies", key = "'all'")
    public CompanyVo createCompany(CreateCompanyRequest request) {
        Company company = new Company();
        company.setName(request.getName());
        company.setDescription(request.getDescription());
        if (request.getWebsites() != null) {
            for (String url : request.getWebsites()) {
                company.addWebsite(url);
            }
        }
        company = companyDataAccess.save(company);
        return companyMapper.toVo(company);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyVo> getAllCompanies() {
        return getAllCompaniesCache().getData();
    }

    @Override
    @Cacheable(value = "companies", key = "'all'", sync = true)
    public CacheListWrapper<CompanyVo> getAllCompaniesCache() {
        return transactionExecutor.executeReadOnly(() -> {
            List<CompanyVo> list = companyDataAccess.findAll().stream()
                    .map(companyMapper::toVo)
                    .toList();
            return new CacheListWrapper<>(list);
        });
    }

    @Override
    @Cacheable(value = "companies", key = "#id", sync = true)
    public CompanyVo getCompanyById(String id) {
        return transactionExecutor.executeReadOnly(() -> {
            UUID uuid = mapUuid(id);
            if (uuid == null) {
                throw new IllegalArgumentException("ID must not be null");
            }
            Company company = companyDataAccess.findById(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Company not found"));
            return companyMapper.toVo(company);
        });
    }

    @Override
    @Transactional
    @Caching(put = {
        @CachePut(value = "companies", key = "#request.id")
    }, evict = {
        @CacheEvict(value = "companies", key = "'all'")
    })
    public CompanyVo updateCompany(UpdateCompanyRequest request) {
        if (request.getId() == null) {
            throw new IllegalArgumentException("ID must not be null");
        }
        Company company = companyDataAccess.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        if (request.getName() != null) {
            company.setName(request.getName());
        }
        if (request.getWebsites() != null) {
            company.getWebsites().clear();
            for (String url : request.getWebsites()) {
                company.addWebsite(url);
            }
        }
        if (request.getDescription() != null) {
            company.setDescription(request.getDescription());
        }
        company = companyDataAccess.save(company);
        return companyMapper.toVo(company);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "companies", key = "#id"),
        @CacheEvict(value = "companies", key = "'all'")
    })
    public void deleteCompany(String id) {
        UUID uuid = mapUuid(id);
        if (uuid == null) {
            throw new IllegalArgumentException("ID must not be null");
        }
        if (!companyDataAccess.existsById(uuid)) {
            throw new IllegalArgumentException("Company not found");
        }
        companyDataAccess.deleteById(uuid);
    }

    @Override
    @Cacheable(value = "companies", key = "'search:' + #query.toString()", sync = true)
    public PageResult<CompanyVo> searchCompanies(CompanySearchQuery query) {
        return transactionExecutor.executeReadOnly(() -> {
            String[] allowedSortFields = {
                "id", "name", "description", "lastScrapedAt",
                "createdBy", "updatedBy", "createdTime", "updatedTime"
            };

            SortFieldValidator.validateSortField(query.getSortBy(), allowedSortFields);
            SortFieldValidator.validateSortDirection(query.getSortDir());

            Page<Company> companyPage = companyDataAccess.searchCompanies(query);

            List<CompanyVo> companyVos = companyPage.getContent().stream()
                    .map(companyMapper::toVo)
                    .toList();

            return PageResult.of(companyPage, companyVos);
        });
    }

    private UUID mapUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }
}
