package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.AlertCheckLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AlertCheckLimitRepository extends JpaRepository<AlertCheckLimit, UUID>, JpaSpecificationExecutor<AlertCheckLimit> {

        @Query(value = "from AlertCheckLimit where tableName = ?1 AND  columnName = ?2 ")
        List<AlertCheckLimit> findAlertCheckLimitByTableNameAndColumnName(
                String tableName, String columnName);



}
