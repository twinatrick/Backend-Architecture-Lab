package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.CompensationRestoreLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CompensationRestoreLogRepository extends JpaRepository<CompensationRestoreLog, UUID> {
}
