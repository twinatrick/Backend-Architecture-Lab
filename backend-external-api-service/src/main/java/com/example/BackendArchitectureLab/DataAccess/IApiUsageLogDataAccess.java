package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.ApiUsageLog;

import java.util.Date;
import java.util.List;

/**
 * ApiUsageLog 資料存取介面。
 * 抽象 ApiUsageLogRepository 供 Service 層使用。
 */
public interface IApiUsageLogDataAccess {

    ApiUsageLog save(ApiUsageLog log);

    List<ApiUsageLog> findByServiceAndCreatedTimeBetween(String service, Date start, Date end);

    List<ApiUsageLog> findByCreatedTimeBetween(Date start, Date end);
}
