package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID>, JpaSpecificationExecutor<Company> {

    @Query("SELECT c.id FROM Company c")
    List<UUID> findAllIds();

    @Query("SELECT DISTINCT c FROM Company c LEFT JOIN FETCH c.websites WHERE c.id IN :ids")
    List<Company> findAllByIdsWithWebsites(@Param("ids") List<UUID> ids);
}
