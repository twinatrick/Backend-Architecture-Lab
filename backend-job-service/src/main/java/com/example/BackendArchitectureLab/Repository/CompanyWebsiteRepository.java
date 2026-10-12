package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.CompanyWebsite;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyWebsiteRepository extends JpaRepository<CompanyWebsite, UUID> {

    @Override
    @EntityGraph(attributePaths = {"company"})
    List<CompanyWebsite> findAll();

    @Override
    @EntityGraph(attributePaths = {"company"})
    Optional<CompanyWebsite> findById(UUID id);

    @EntityGraph(attributePaths = {"company"})
    @Query("SELECT cw FROM CompanyWebsite cw WHERE cw.company.id = :companyId")
    List<CompanyWebsite> findByCompanyId(@Param("companyId") UUID companyId);
}
