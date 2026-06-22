package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Entity.ApiUsageLog;
import com.example.BackendArchitectureLab.Mapper.ApiUsageLogMapper;
import com.example.BackendArchitectureLab.Repository.ApiUsageLogRepository;
import com.example.BackendArchitectureLab.Vo.ApiUsageLogVo;
import com.example.BackendArchitectureLab.Service.IApiUsageLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class ApiUsageLogService implements IApiUsageLogService {

    @Autowired
    private ApiUsageLogRepository apiUsageLogRepository;

    @Autowired
    private ApiUsageLogMapper apiUsageLogMapper;

    @Override
    public List<ApiUsageLogVo> findByRange(Date start, Date end, String service) {
        List<ApiUsageLog> logs;
        if (service != null) {
            logs = apiUsageLogRepository.findByServiceAndCreatedTimeBetweenOrderByCreatedTimeDesc(service, start, end);
        } else {
            logs = apiUsageLogRepository.findByCreatedTimeBetweenOrderByCreatedTimeDesc(start, end);
        }
        return logs.stream().map(apiUsageLogMapper::toDto).toList();
    }

    @Override
    public Map<String, Object> getSummary(Date start, Date end, String service) {
        List<ApiUsageLog> logs;
        if (service != null) {
            logs = apiUsageLogRepository.findByServiceAndCreatedTimeBetweenOrderByCreatedTimeDesc(service, start, end);
        } else {
            logs = apiUsageLogRepository.findByCreatedTimeBetweenOrderByCreatedTimeDesc(start, end);
        }

        long totalCalls = logs.size();
        BigDecimal totalCost = logs.stream()
                .map(ApiUsageLog::getEstimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalInput = logs.stream()
                .mapToLong(l -> l.getInputAmount() != null ? l.getInputAmount() : 0L)
                .sum();

        return Map.of(
                "totalCalls", totalCalls,
                "totalCost", totalCost,
                "totalInput", totalInput,
                "start", start,
                "end", end,
                "service", service
        );
    }
}
