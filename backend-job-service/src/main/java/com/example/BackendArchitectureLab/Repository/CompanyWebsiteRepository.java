package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.CompanyWebsite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompanyWebsiteRepository extends JpaRepository<CompanyWebsite, UUID> {
    @Query("SELECT cw FROM CompanyWebsite cw WHERE cw.company.id = :companyId")
    List<CompanyWebsite> findByCompanyId(@org.springframework.data.repository.query.Param("companyId") UUID companyId);
}
