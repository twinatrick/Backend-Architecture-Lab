package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IApiUsageLogDataAccess;
import com.example.BackendArchitectureLab.Entity.ApiUsageLog;
import com.example.BackendArchitectureLab.Repository.ApiUsageLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * IApiUsageLogDataAccess 實作。
 * 委派 ApiUsageLogRepository 執行資料存取。
 */
@Component
public class ApiUsageLogDataAccessImpl implements IApiUsageLogDataAccess {

    @Autowired
    private ApiUsageLogRepository apiUsageLogRepository;

    @Override
    public ApiUsageLog save(ApiUsageLog log) {
        return apiUsageLogRepository.save(log);
    }

    @Override
    public List<ApiUsageLog> findByServiceAndCreatedTimeBetween(String service, Date start, Date end) {
        return apiUsageLogRepository.findByServiceAndCreatedTimeBetweenOrderByCreatedTimeDesc(service, start, end);
    }

    @Override
    public List<ApiUsageLog> findByCreatedTimeBetween(Date start, Date end) {
        return apiUsageLogRepository.findByCreatedTimeBetweenOrderByCreatedTimeDesc(start, end);
    }
}
