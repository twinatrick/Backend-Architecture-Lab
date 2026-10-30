package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.ApiUsageLogVo;
import com.example.BackendArchitectureLab.Vo.ApiUsageSummaryVo;

import java.util.Date;
import java.util.List;

public interface IApiUsageLogService {
    List<ApiUsageLogVo> findByRange(Date start, Date end, String service);
    ApiUsageSummaryVo getSummary(Date start, Date end, String service);
}
