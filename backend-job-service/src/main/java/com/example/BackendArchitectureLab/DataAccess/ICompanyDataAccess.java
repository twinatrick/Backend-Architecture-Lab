package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.Company;
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
     * Find a company by its ID.
     *
     * @param id the company UUID
     * @return optional containing the company if found
     */
    Optional<Company> findById(UUID id);

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
}
