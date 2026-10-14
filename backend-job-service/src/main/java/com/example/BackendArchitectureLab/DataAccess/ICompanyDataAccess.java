package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Vo.Search.CompanySearchQuery;
import com.example.BackendArchitectureLab.Entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access interface for Company entity operations.
 */
public interface ICompanyDataAccess {

    /**
     * Save a company entity.
     *
     * @param company the company to save
     * @return the saved company
     */
    Company save(Company company);

    /**
     * Find all companies.
     *
     * @return list of all companies
     */
    List<Company> findAll();

    /**
     * 分頁查詢所有公司
     *
     * @param pageable 分頁與排序參數
     * @return 分頁公司實體
     */
    Page<Company> findAllPaged(Pageable pageable);

    /**
     * Find a company by its ID.
     *
     * @param id the company UUID
     * @return optional containing the company if found
     */
    Optional<Company> findById(UUID id);

    /**
     * Find all companies matching given IDs.
     *
     * @param ids the list of company UUIDs
     * @return list of companies found
     */
    List<Company> findAllById(List<UUID> ids);

    /**
     * Check if a company exists by its ID.
     *
     * @param id the company UUID
     * @return true if company exists, false otherwise
     */
    boolean existsById(UUID id);

    /**
     * Delete a company by its ID.
     *
     * @param id the company UUID
     */
    void deleteById(UUID id);

    /**
     * 分頁查詢公司
     *
     * @param query 查詢參數
     * @return 分頁結果
     */
    Page<Company> searchCompanies(CompanySearchQuery query);
}
