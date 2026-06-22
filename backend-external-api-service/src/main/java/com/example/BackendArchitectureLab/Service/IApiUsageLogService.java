package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.ApiUsageLogVo;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface IApiUsageLogService {
    List<ApiUsageLogVo> findByRange(Date start, Date end, String service);
    Map<String, Object> getSummary(Date start, Date end, String service);
}
