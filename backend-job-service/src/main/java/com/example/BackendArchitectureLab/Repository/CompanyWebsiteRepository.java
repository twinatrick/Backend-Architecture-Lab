package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.CompanyWebsite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CompanyWebsiteRepository extends JpaRepository<CompanyWebsite, UUID> {
}
