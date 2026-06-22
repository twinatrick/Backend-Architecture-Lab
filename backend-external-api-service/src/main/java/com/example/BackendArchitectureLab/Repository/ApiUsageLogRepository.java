package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.ApiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, UUID> {
    List<ApiUsageLog> findByCreatedTimeBetweenOrderByCreatedTimeDesc(Date createdTime, Date createdTime2);
    List<ApiUsageLog> findByServiceAndCreatedTimeBetweenOrderByCreatedTimeDesc(String service, Date createdTime, Date createdTime2);
}
