package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.JobPosting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, UUID>, JpaSpecificationExecutor<JobPosting> {

    @Override
    @EntityGraph(attributePaths = {"company"})
    List<JobPosting> findAll();

    @Override
    @EntityGraph(attributePaths = {"company"})
    List<JobPosting> findAllById(Iterable<UUID> ids);

    @Override
    @EntityGraph(attributePaths = {"company"})
    Page<JobPosting> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"company"})
    Page<JobPosting> findAll(Specification<JobPosting> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"company"})
    Optional<JobPosting> findById(UUID id);

    @Query("SELECT j.id FROM JobPosting j")
    List<UUID> findAllIds();

    @Query("SELECT jp FROM JobPosting jp WHERE jp.company.id = :companyId")
    @EntityGraph(attributePaths = {"company"})
    List<JobPosting> findByCompanyId(@Param("companyId") UUID companyId);
}
