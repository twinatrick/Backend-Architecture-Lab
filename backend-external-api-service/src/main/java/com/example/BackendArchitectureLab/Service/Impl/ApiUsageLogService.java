package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IApiUsageLogDataAccess;
import com.example.BackendArchitectureLab.Entity.ApiUsageLog;
import com.example.BackendArchitectureLab.Mapper.ApiUsageLogMapper;
import com.example.BackendArchitectureLab.Vo.ApiUsageLogVo;
import com.example.BackendArchitectureLab.Service.IApiUsageLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ApiUsageLogService implements IApiUsageLogService {

    private final IApiUsageLogDataAccess apiUsageLogDataAccess;
    private final ApiUsageLogMapper apiUsageLogMapper;

    @Override
    public List<ApiUsageLogVo> findByRange(Date start, Date end, String service) {
        List<ApiUsageLog> logs;
        if (service != null) {
            logs = apiUsageLogDataAccess.findByServiceAndCreatedTimeBetween(service, start, end);
        } else {
            logs = apiUsageLogDataAccess.findByCreatedTimeBetween(start, end);
        }
        return logs.stream().map(apiUsageLogMapper::toVo).toList();
    }

    @Override
    public Map<String, Object> getSummary(Date start, Date end, String service) {
        List<ApiUsageLog> logs;
        if (service != null) {
            logs = apiUsageLogDataAccess.findByServiceAndCreatedTimeBetween(service, start, end);
        } else {
            logs = apiUsageLogDataAccess.findByCreatedTimeBetween(start, end);
        }

        long totalCalls = logs.size();
        BigDecimal totalCost = logs.stream()
                .map(ApiUsageLog::getEstimatedCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalInput = logs.stream()
                .mapToLong(l -> l.getInputAmount() != null ? l.getInputAmount() : 0L)
                .sum();

        Map<String, Object> result = new HashMap<>();
        result.put("totalCalls", totalCalls);
        result.put("totalCost", totalCost);
        result.put("totalInput", totalInput);
        result.put("start", start);
        result.put("end", end);
        if (service != null) {
            result.put("service", service);
        }
        return result;
    }
}
