package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, UUID>, JpaSpecificationExecutor<JobPosting> {

    @Query("SELECT j.id FROM JobPosting j")
    List<UUID> findAllIds();
}
